package io.github.byjoelsamuel.drivesanity

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A number that counts up and down smoothly instead of jumping, drawn like an odometer: each digit
 * rolls to the next one, and while the number is changing fast the rolling digits get a motion smear.
 */
class RollingNumber {
    /** What's on screen right now, easing towards the real value. */
    var shown = 0f
        private set

    /** How fast it's changing, in units per second (smoothed). */
    var rate = 0f
        private set

    private var lastNs = 0L

    /** Moves the shown number towards [target]. Null (no reading) resets it. */
    fun update(target: Float?, nowNs: Long = System.nanoTime()): Float? {
        if (target == null) {
            lastNs = 0L
            rate = 0f
            return null
        }
        if (lastNs == 0L) {
            shown = target
            lastNs = nowNs
            return shown
        }
        val dt = ((nowNs - lastNs) / 1e9f).coerceIn(0.001f, 0.25f)
        lastNs = nowNs
        val prev = shown
        var next = shown + (target - shown) * (1f - exp(-dt / TAU))
        if (abs(target - next) < 0.03f) next = target
        shown = next
        rate += ((shown - prev) / dt - rate) * 0.35f
        return shown
    }

    /**
     * Draws [value] centred on [cx], digits sitting on [baseline]. [p] supplies the typeface; its
     * size, alignment and colour are set here. Returns the width of the digits.
     */
    fun draw(c: Canvas, p: Paint, value: Float, rate: Float, cx: Float, baseline: Float, size: Float, color: Int): Float {
        p.textSize = size
        p.textAlign = Paint.Align.CENTER
        val n = max(0, floor(value.toDouble()).toInt())
        val f = value - n
        val cell = p.measureText("0")
        val nLen = n.toString().length
        val len = if (f > 0f && (n + 1).toString().length > nLen) nLen + 1 else nLen
        val travel = size * 0.82f
        val smear = min(1f, abs(rate) / 12f)

        c.save()
        c.clipRect(cx - len * cell / 2 - cell * 0.15f, baseline - size * 0.88f, cx + len * cell / 2 + cell * 0.15f, baseline + size * 0.14f)
        var unit = 1
        for (k in 0 until len) {
            val cur = (n / unit) % 10
            val next = ((n + 1) / unit) % 10
            val progress = if (k == 0 || n % unit == unit - 1) f else 0f
            val x = cx + ((len - 1) / 2f - k) * cell
            fun glyph(d: Int, dy: Float, alpha: Float) {
                p.color = withAlpha(color, alpha)
                c.drawText(d.toString(), x, baseline + dy, p)
            }
            if (progress <= 0f) {
                if (k < nLen) glyph(cur, 0f, 1f)
            } else {
                if (k < nLen) glyph(cur, -progress * travel, 1f - progress)
                glyph(next, (1f - progress) * travel, progress)
                if (smear > 0.15f) {
                    // The smear trails behind the motion.
                    val dir = if (rate >= 0f) 1f else -1f
                    for (g in 1..3) {
                        val off = dir * smear * travel * 0.16f * g
                        if (k < nLen) glyph(cur, -progress * travel + off, (1f - progress) * 0.13f)
                        glyph(next, (1f - progress) * travel + off, progress * 0.13f)
                    }
                }
            }
            unit *= 10
        }
        c.restore()
        return len * cell
    }

    /** Width of [value] drawn at the paint's current size, for laying out things beside it. */
    fun width(p: Paint, value: Float): Float {
        val n = max(0, floor(value.toDouble()).toInt())
        return n.toString().length * p.measureText("0")
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).roundToInt().coerceIn(0, 255) shl 24)

    companion object {
        private const val TAU = 0.22f
    }
}
