package io.github.byjoelsamuel.drivesanity.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.SystemClock
import android.util.LruCache
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.nav.Http
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Map tiles, fetched as needed, kept on disk for a week and in memory. Only tiles on screen are
 * asked for. Two looks:
 *
 *  - Modern (the default): CARTO's "Dark Matter", a dark map that's already styled for this, fetched
 *    at double resolution so it stays sharp on the car's screen.
 *  - Classic: OpenStreetMap's standard tiles, turned into a dark grey version here.
 *
 * Map data © OpenStreetMap contributors, and for the modern look the style © CARTO.
 */
object Tiles {
    private const val CLASSIC_URL = "https://tile.openstreetmap.org/%d/%d/%d.png"
    private const val MODERN_URL = "https://%s.basemaps.cartocdn.com/dark_all/%d/%d/%d@2x.png"
    private const val WEEK_MS = 7L * 24 * 3600 * 1000

    private var dir: File? = null

    /** Which look is on. Changing it starts the map afresh, since the two don't share tiles. */
    @Volatile var modern = true
        private set

    val attribution: String get() = if (modern) "© OpenStreetMap © CARTO" else "© OpenStreetMap"

    fun setModern(on: Boolean) {
        if (on == modern) return
        modern = on
        memory.evictAll()
        failedAt.clear()
        loading.clear()
    }

    private val memory = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val pool = Executors.newFixedThreadPool(2) { Thread(it, "tiles").apply { isDaemon = true } }

    /**
     * Inverts brightness and squeezes it into a dark range: land goes near-black, roads stay
     * dark with lighter edges, labels come out light grey. A touch of blue keeps it from
     * looking flat.
     */
    private val darken = ColorMatrixColorFilter(ColorMatrix().apply {
        val k = 0.5f
        fun row(base: Float) = floatArrayOf(-k * 0.299f, -k * 0.587f, -k * 0.114f, 0f, base + 255f * k)
        set(row(16f) + row(18f) + row(24f) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    })

    fun init(context: Context) {
        if (dir == null) dir = File(context.cacheDir, "tiles")
        modern = Prefs.modernMap(context)
    }

    /** The tile if it's ready; otherwise null, and it gets fetched in the background. */
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val n = 1 shl z
        if (y !in 0 until n) return null
        val wx = ((x % n) + n) % n
        val key = (if (modern) "m" else "c") + "$z/$wx/$y"
        memory.get(key)?.let { return it }
        val failed = failedAt[key]
        if (failed != null && SystemClock.elapsedRealtime() - failed < 30_000) return null
        if (dir == null || !loading.add(key)) return null
        val isModern = modern
        pool.execute { load(key, z, wx, y, isModern) }
        return null
    }

    private fun load(key: String, z: Int, x: Int, y: Int, isModern: Boolean) {
        try {
            val file = File(dir, "$key.png")
            val bytes = if (file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK_MS) {
                file.readBytes()
            } else {
                Http.get(if (isModern) MODERN_URL.format("abcd"[(x + y) and 3].toString(), z, x, y) else CLASSIC_URL.format(z, x, y)).also { b ->
                    file.parentFile?.mkdirs()
                    file.writeBytes(b)
                }
            }
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("bad tile")
            val out = if (isModern) {
                src // already dark
            } else {
                Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also {
                    Canvas(it).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = darken })
                    src.recycle()
                }
            }
            if (isModern != modern) return // the look was changed while this was loading
            memory.put(key, out)
            failedAt.remove(key)
        } catch (_: Exception) {
            failedAt[key] = SystemClock.elapsedRealtime()
        } finally {
            loading.remove(key)
        }
    }
}
