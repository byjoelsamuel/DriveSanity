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
 * A soft CRT power-on, in the spirit of an early-2000s handheld's boot screen: a glowing line
 * stretches across a dark screen and swells until it fills it with light, the name fades in
 * large and glowing, and the whole thing eases away into the app. [t] is seconds since the
 * start; it ends at [DURATION].
 */
class StartupPainter(context: Context) {
    private val word = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.semibold(context) }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium(context) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scan = Paint().apply { color = Color.argb(30, 0, 0, 0) }
    private val vig = Paint()
    private val glow = Paint()
    private val bloom = Paint().apply {
        // A unit circle, stretched to fit the line when drawn.
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(120, 190, 215, 240), Color.argb(46, 160, 190, 220), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private var linesFor = RectF()
    private var lines = FloatArray(0)
    private var shadersFor = RectF()

    fun draw(c: Canvas, r: RectF, t: Float) {
        val fade = 1f - smooth(t, 2.4f, 3.0f)
        if (fade <= 0f) return
        val layer = c.saveLayerAlpha(r, (fade * 255).roundToInt())
        val w = r.width()
        val h = r.height()
        val cx = r.centerX()
        val cy = r.centerY()
        fill.color = BLACK
        c.drawRect(r, fill)
        shaders(r)

        // A glowing line grows out from the middle, then swells until it is the whole screen.
        val grow = smooth(t, 0.1f, 0.55f)
        val open = smooth(t, 0.5f, 1.2f)
        val thin = max(3f, h * 0.012f)
        val halfH = thin / 2 + (h / 2 - thin / 2) * open * open
        val halfW = w / 2 * grow
        // A soft ellipse of light around the line, fading out in every direction.
        if (halfW > 1f) {
            bloom.alpha = (255 * (1f - open * 0.75f)).roundToInt()
            c.save()
            c.translate(cx, cy)
            c.scale(halfW + thin * 6f, halfH + thin * 9f * (1f - open * 0.5f))
            c.drawCircle(0f, 0f, 1f, bloom)
            c.restore()
        }
        fill.color = mix(HOT, PHOSPHOR, open)
        c.drawRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, fill)
        // Light from the middle fills the screen.
        glow.alpha = (open * 255).roundToInt()
        c.drawRect(r, glow)

        // The name fades in, big and glowing.
        val logo = smooth(t, 1.05f, 1.7f)
        if (logo > 0f) {
            val flicker = if (t < 1.6f) 0.92f + 0.08f * sin(t * 70f) else 1f
            word.textSize = min(w * 0.15f, h * 0.28f)
            word.textAlign = Paint.Align.CENTER
            word.letterSpacing = 0.04f
            word.color = Color.argb((logo * flicker * 255).roundToInt().coerceIn(0, 255), 238, 236, 231)
            word.setShadowLayer(word.textSize * 0.4f, 0f, 0f, Color.argb((190 * logo).roundToInt(), 143, 179, 194))
            val y = cy + word.textSize * 0.1f
            c.drawText("DriveSanity", cx, y, word)

            val sweep = smooth(t, 1.4f, 2.0f)
            val half = word.measureText("DriveSanity") / 2 * sweep
            fill.color = Color.argb((190 * logo).roundToInt(), 143, 179, 194)
            c.drawRect(cx - half, y + word.textSize * 0.2f, cx + half, y + word.textSize * 0.2f + thin * 0.7f, fill)

            val tag = smooth(t, 1.8f, 2.3f)
            small.textSize = word.textSize * 0.2f
            small.textAlign = Paint.Align.CENTER
            small.letterSpacing = 0.3f
            small.color = Color.argb((tag * 255).roundToInt(), 150, 154, 160)
            c.drawText("DRIVE SYSTEM ONLINE", cx, y + word.textSize * 0.66f, small)
        }

        scanlines(c, r)
        c.drawRect(r, vig)
        c.restoreToCount(layer)
    }

    private fun shaders(r: RectF) {
        if (shadersFor == r) return
        shadersFor.set(r)
        val reach = hypot(r.width(), r.height()) / 2f
        glow.shader = RadialGradient(
            r.centerX(), r.centerY(), reach * 0.9f,
            intArrayOf(Color.argb(110, 120, 155, 190), Color.argb(40, 70, 95, 125), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        vig.shader = RadialGradient(
            r.centerX(), r.centerY(), reach,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(110, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
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

    companion object {
        const val DURATION = 3.0f
        private val BLACK = Color.rgb(4, 5, 6)
        private val HOT = Color.rgb(240, 248, 255)
        private val PHOSPHOR = Color.rgb(22, 28, 36)

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
