package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A short CRT power-on, in the spirit of an early-2000s handheld's boot screen: a bright line
 * stretches across a black screen, opens up into a glowing picture, the name flickers in, and
 * the whole thing, under scanlines and darkened corners, fades away into the app.
 * [t] is seconds since the start; it ends at [DURATION].
 */
class StartupPainter(context: Context) {
    private val word = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium(context) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scan = Paint().apply { color = Color.argb(70, 0, 0, 0) }
    private val vig = Paint()
    private var linesFor = RectF()
    private var lines = FloatArray(0)
    private var vigFor = RectF()

    fun draw(c: Canvas, r: RectF, t: Float) {
        val fade = 1f - smooth(t, 2.15f, 2.6f)
        if (fade <= 0f) return
        val layer = c.saveLayerAlpha(r, (fade * 255).roundToInt())
        val w = r.width()
        val h = r.height()
        val cx = r.centerX()
        val cy = r.centerY()
        fill.color = BLACK
        c.drawRect(r, fill)

        // The line stretches out from the middle, then opens up into the picture.
        val grow = smooth(t, 0.12f, 0.45f)
        val open = smooth(t, 0.45f, 0.95f)
        val thin = max(2f, h * 0.008f)
        val halfH = thin / 2 + (h / 2 - thin / 2) * open * open
        val halfW = w / 2 * grow
        val bloomH = halfH + thin * 3 * (1f - open)
        fill.color = Color.argb((90 * (1f - open)).roundToInt(), 190, 215, 235)
        c.drawRect(cx - halfW, cy - bloomH, cx + halfW, cy + bloomH, fill)
        fill.color = mix(HOT, PHOSPHOR, open)
        c.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, fill)

        // The name flickers in.
        val logo = smooth(t, 1.0f, 1.55f)
        if (logo > 0f) {
            val flicker = if (t < 1.5f) 0.85f + 0.15f * sin(t * 90f) else 1f
            val jitter = if (t < 1.35f) ((t * 997f) % 1f - 0.5f) * w * 0.004f else 0f
            word.textSize = min(w * 0.11f, h * 0.2f)
            word.textAlign = Paint.Align.CENTER
            word.letterSpacing = 0.04f
            word.color = Color.argb((logo * flicker * 255).roundToInt().coerceIn(0, 255), 232, 230, 225)
            word.setShadowLayer(word.textSize * 0.25f, 0f, 0f, Color.argb((170 * logo).roundToInt(), 143, 179, 194))
            val y = cy + word.textSize * 0.1f
            c.drawText("DriveSanity", cx + jitter, y, word)

            val sweep = smooth(t, 1.3f, 1.8f)
            val half = word.measureText("DriveSanity") / 2 * sweep
            fill.color = Color.argb((200 * logo).roundToInt(), 143, 179, 194)
            c.drawRect(cx - half, y + word.textSize * 0.18f, cx + half, y + word.textSize * 0.18f + thin, fill)

            val tag = smooth(t, 1.6f, 1.95f)
            small.textSize = word.textSize * 0.2f
            small.textAlign = Paint.Align.CENTER
            small.letterSpacing = 0.3f
            small.color = Color.argb((tag * 255).roundToInt(), 128, 132, 138)
            c.drawText("DRIVE SYSTEM ONLINE", cx, y + word.textSize * 0.62f, small)
        }

        // A faint refresh band rolling down the screen.
        if (open > 0.9f) {
            fill.color = Color.argb(14, 255, 255, 255)
            val top = r.top + ((t * 0.7f) % 1f) * h
            c.drawRect(r.left, top, r.right, min(r.bottom, top + h * 0.12f), fill)
        }
        scanlines(c, r)
        vignette(c, r)
        c.restoreToCount(layer)
    }

    private fun scanlines(c: Canvas, r: RectF) {
        val step = max(3f, r.height() / 200f)
        if (linesFor != r) {
            linesFor.set(r)
            val n = (r.height() / step).toInt()
            lines = FloatArray(n * 4)
            for (i in 0 until n) {
                val y = r.top + i * step + step * 0.5f
                lines[i * 4] = r.left
                lines[i * 4 + 1] = y
                lines[i * 4 + 2] = r.right
                lines[i * 4 + 3] = y
            }
            scan.strokeWidth = step * 0.45f
        }
        c.drawLines(lines, scan)
    }

    private fun vignette(c: Canvas, r: RectF) {
        if (vigFor != r) {
            vigFor.set(r)
            vig.shader = RadialGradient(
                r.centerX(), r.centerY(), hypot(r.width(), r.height()) / 2f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(r, vig)
    }

    companion object {
        const val DURATION = 2.6f
        private val BLACK = Color.rgb(4, 5, 6)
        private val HOT = Color.rgb(245, 250, 255)
        private val PHOSPHOR = Color.rgb(20, 26, 32)

        private fun smooth(t: Float, a: Float, b: Float): Float {
            val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
            return x * x * (3 - 2 * x)
        }

        private fun mix(a: Int, b: Int, k: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * k).roundToInt()
            return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
        }
    }
}
