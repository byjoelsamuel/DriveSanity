package io.github.byjoelsamuel.drivesanity.mirror

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Google Maps on the car screen, by copying the phone's screen: the capture service puts each picture here,
 * and the car's "Google Maps" screen draws the latest one. It's view-only. Taps on the car screen can't reach
 * Google Maps, so the phone is where you search or change the route.
 */
object MapMirror {
    sealed interface State {
        data object Off : State
        data object Asking : State
        data class Sharing(val sinceMs: Long) : State
        data class Failed(val message: String) : State
    }

    /** What the phone's Settings page shows. */
    var state: State by mutableStateOf(State.Off)
        internal set

    private val lock = Any()
    private var frame: Bitmap? = null
    private var frameW = 0
    private var frameH = 0
    private var frameAtMs = 0L

    /** A picture older than this is treated as "nothing coming through" (the phone's screen is off, or Maps closed). */
    const val STALE_MS = 4_000L

    /**
     * The largest size to capture at: the phone's screen scaled so its long side is at most [maxLong],
     * with both sides even, which the encoder behind the capture wants.
     */
    fun captureSize(displayW: Int, displayH: Int, maxLong: Int = 1280): Pair<Int, Int> {
        val scale = minOf(1f, maxLong / maxOf(displayW, displayH).toFloat())
        fun even(v: Float) = maxOf(2, (v.toInt() / 2) * 2)
        return even(displayW * scale) to even(displayH * scale)
    }

    /** Where a [w] by [h] picture goes inside [into]: as large as fits, centred, never stretched or cut. */
    fun fit(w: Int, h: Int, into: RectF): RectF {
        if (w <= 0 || h <= 0 || into.isEmpty) return RectF(into.centerX(), into.centerY(), into.centerX(), into.centerY())
        val k = minOf(into.width() / w, into.height() / h)
        val dw = w * k
        val dh = h * k
        return RectF(into.centerX() - dw / 2, into.centerY() - dh / 2, into.centerX() + dw / 2, into.centerY() + dh / 2)
    }

    /**
     * The capture service hands over its pixel rows here. [bufferWidth] can be wider than [w], because rows are
     * padded; only the first [w] pixels of each row are drawn. [copyInto] fills the stored bitmap.
     */
    fun publish(bufferWidth: Int, w: Int, h: Int, nowMs: Long = System.currentTimeMillis(), copyInto: (Bitmap) -> Unit) {
        synchronized(lock) {
            var bmp = frame
            if (bmp == null || bmp.width != bufferWidth || bmp.height != h) {
                bmp?.recycle()
                bmp = Bitmap.createBitmap(bufferWidth, h, Bitmap.Config.ARGB_8888)
                frame = bmp
            }
            copyInto(bmp)
            frameW = w
            frameH = h
            frameAtMs = nowMs
        }
    }

    fun hasFreshFrame(nowMs: Long = System.currentTimeMillis()): Boolean =
        synchronized(lock) { frame != null && nowMs - frameAtMs <= STALE_MS }

    /** Draws the latest picture, fitted into [into]. Returns where it went, or null if there's nothing fresh to show. */
    fun draw(c: Canvas, into: RectF, paint: Paint, nowMs: Long = System.currentTimeMillis()): RectF? = synchronized(lock) {
        val bmp = frame
        if (bmp == null || frameW <= 0 || nowMs - frameAtMs > STALE_MS) return null
        val dst = fit(frameW, frameH, into)
        c.drawBitmap(bmp, Rect(0, 0, frameW, frameH), dst, paint)
        dst
    }

    /** Forgets the picture, so a later start doesn't flash the last one. */
    fun clear() {
        synchronized(lock) {
            frame?.recycle()
            frame = null
            frameW = 0
            frameH = 0
        }
    }

    fun stop(ctx: Context) = MirrorService.stop(ctx)
}
