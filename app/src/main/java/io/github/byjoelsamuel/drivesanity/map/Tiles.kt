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
import io.github.byjoelsamuel.drivesanity.nav.Http
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Map tiles, fetched as needed, kept on disk for a week and in memory. Only tiles on screen are
 * asked for. They're OpenStreetMap's standard tiles, turned into a dark grey version here. (CARTO's
 * ready-made dark tiles were tried and need an API key, so they're not used.)
 *
 * Map data © OpenStreetMap contributors.
 */
object Tiles {
    private const val CLASSIC_URL = "https://tile.openstreetmap.org/%d/%d/%d.png"
    private const val WEEK_MS = 7L * 24 * 3600 * 1000

    private var dir: File? = null

    val attribution: String get() = "© OpenStreetMap"

    /** Throws away every saved tile, in memory and on disk; they're fetched again as the map needs them. */
    fun clear(context: Context) {
        memory.evictAll()
        failedAt.clear()
        loading.clear()
        File(context.cacheDir, "tiles").listFiles()?.forEach { it.delete() }
    }

    /** What the saved tiles take on disk, in bytes. */
    fun cacheBytes(context: Context): Long = File(context.cacheDir, "tiles").listFiles()?.sumOf { it.length() } ?: 0L

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
    }

    /** The tile if it's ready; otherwise null, and it gets fetched in the background. */
    fun get(z: Int, x: Int, y: Int): Bitmap? {
        val n = 1 shl z
        if (y !in 0 until n) return null
        val wx = ((x % n) + n) % n
        val key = "c$z/$wx/$y"
        memory.get(key)?.let { return it }
        val failed = failedAt[key]
        if (failed != null && SystemClock.elapsedRealtime() - failed < 30_000) return null
        if (dir == null || !loading.add(key)) return null
        pool.execute { load(key, z, wx, y) }
        return null
    }

    private fun load(key: String, z: Int, x: Int, y: Int) {
        try {
            val file = File(dir, "$key.png")
            val bytes = if (file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK_MS) {
                file.readBytes()
            } else {
                Http.get(CLASSIC_URL.format(z, x, y)).also { b ->
                    file.parentFile?.mkdirs()
                    file.writeBytes(b)
                }
            }
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("bad tile")
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = darken })
                src.recycle()
            }
            memory.put(key, out)
            failedAt.remove(key)
        } catch (_: Exception) {
            failedAt[key] = SystemClock.elapsedRealtime()
        } finally {
            loading.remove(key)
        }
    }
}
