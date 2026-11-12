package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The retro screen: everything in Saab's instrument green on black, after early-2000s
 * dashboards. A rising bar-graph tach like the C4 Corvette's, seven-segment digits with the
 * unlit segments faintly showing, and a bank of small vertical gauges. Words are kept to
 * short labels.
 */
class RetroPainter(context: Context) {
    private val font = Fonts.semibold(context)
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GREEN }
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GHOST }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = MID }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; letterSpacing = 0.18f }
    private val seg = Path()

    /** Half the time, for blinking warnings. */
    private val blinkOn get() = (SystemClock.uptimeMillis() / 250) % 2 == 0L

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        ghost.color = BG
        c.drawRect(r, ghost)
        ghost.color = GHOST
        // A soft glow on everything lit, like a backlit panel.
        lit.setShadowLayer(min(r.width(), r.height()) * 0.012f, 0f, 0f, GLOW)
        if (r.width() > r.height() * 1.2f) drawWide(c, r, s, units) else drawTall(c, r, s, units)
        lit.clearShadowLayer()
    }

    private fun drawWide(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val pad = r.height() * 0.035f
        val b = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val w = b.width()
        val h = b.height()
        fun box(x0: Float, y0: Float, x1: Float, y1: Float) = RectF(b.left + w * x0, b.top + h * y0, b.left + w * x1, b.top + h * y1)

        drawTach(c, box(0f, 0f, 1f, 0.30f), s)
        drawBars(c, box(0f, 0.36f, 0.30f, 0.80f), s, units)
        drawSpeed(c, box(0.33f, 0.36f, 0.72f, 0.80f), s, units)
        val scope = min(w * 0.25f, h * 0.46f)
        drawScope(c, RectF(b.right - scope, b.top + h * 0.35f, b.right, b.top + h * 0.35f + scope), s)
        drawBrake(c, box(0f, 0.86f, 0.27f, 1f), s)
        drawTires(c, box(0.30f, 0.84f, 0.62f, 1f), s, units)
        drawTrip(c, box(0.65f, 0.86f, 0.80f, 1f), s, units)
        drawLamps(c, box(0.83f, 0.88f, 1f, 1f), s)
    }

    private fun drawTall(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val pad = r.width() * 0.06f
        val b = RectF(r.left + pad, r.top + pad * 0.6f, r.right - pad, r.bottom - pad * 0.6f)
        val w = b.width()
        val h = b.height()
        fun box(x0: Float, y0: Float, x1: Float, y1: Float) = RectF(b.left + w * x0, b.top + h * y0, b.left + w * x1, b.top + h * y1)

        drawLamps(c, box(0f, 0f, 1f, 0.035f), s)
        drawTach(c, box(0f, 0.06f, 1f, 0.22f), s)
        drawSpeed(c, box(0f, 0.25f, 1f, 0.45f), s, units)
        drawBars(c, box(0.04f, 0.48f, 0.96f, 0.71f), s, units)
        drawBrake(c, box(0f, 0.74f, 1f, 0.80f), s)
        val scope = min(w * 0.5f, h * 0.18f)
        drawScope(c, RectF(b.left, b.bottom - scope, b.left + scope, b.bottom), s)
        drawTrip(c, box(0.55f, 0.86f, 1f, 0.97f), s, units)
    }

    // --- pieces -----------------------------------------------------------------------------

    /** C4-style: 250 rpm per bar, bars rising to the right; past the redline they're hollow when off. */
    private fun drawTach(c: Canvas, b: RectF, s: Snapshot) {
        val rpm = s.rpm
        val over = rpm != null && rpm >= Revs.REDLINE
        val n = (Revs.MAX / 250f).toInt()
        val labelH = b.height() * 0.14f
        val bottom = b.bottom - labelH * 1.3f
        val maxH = bottom - b.top
        val step = b.width() / n
        val gap = step * 0.28f
        line.strokeWidth = step * 0.08f
        for (i in 0 until n) {
            val from = i * 250f
            val barH = maxH * (0.22f + 0.78f * ((i + 1f) / n).pow(1.6f))
            val x = b.left + i * step + gap / 2
            val rect = RectF(x, bottom - barH, x + step - gap, bottom)
            val on = rpm != null && rpm > from && (!over || blinkOn)
            when {
                on -> c.drawRect(rect, lit)
                from + 250f > Revs.REDLINE -> c.drawRect(rect, line)
                else -> c.drawRect(rect, ghost)
            }
        }
        label(labelH, MID, Paint.Align.CENTER)
        for (k in 0..(Revs.MAX / 1000f).toInt()) {
            val x = (b.left + k * 1000f / Revs.MAX * b.width()).coerceIn(b.left + labelH, b.right - labelH)
            c.drawText(k.toString(), x, b.bottom, text)
        }
        // The number sits in the empty corner above the short bars.
        val digitH = maxH * 0.42f
        drawDigits(c, rpm?.let { ((it / 50f).roundToInt() * 50).toString() } ?: "", b.left, b.top, digitH, 4)
        label(digitH * 0.3f, MID, Paint.Align.LEFT)
        c.drawText("RPM", b.left + cellsWidth(4, digitH) + digitH * 0.25f, b.top + digitH, text)
    }

    /** The big speed, km/h small under it, and the gear in a box. */
    private fun drawSpeed(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val big = min(b.height() * 0.62f, b.width() / 2.6f)
        val gearW = big * 0.75f
        val speedW = cellsWidth(3, big)
        val total = speedW + big * 0.5f + gearW
        val x = b.centerX() - total / 2
        val y = b.top + b.height() * 0.06f
        drawDigits(c, s.speedMps?.let { units.speed(it).roundToInt().toString() } ?: "", x, y, big, 3)
        label(big * 0.17f, MID, Paint.Align.LEFT)
        c.drawText(units.label.uppercase(), x, y + big + big * 0.3f, text)
        val small = big * 0.3f
        val other = units.other
        val ox = x + speedW - cellsWidth(3, small)
        drawDigits(c, s.speedMps?.let { other.speed(it).roundToInt().toString() } ?: "", ox, y + big + big * 0.1f, small, 3)
        label(small * 0.5f, MID, Paint.Align.RIGHT)
        c.drawText(other.label.uppercase(), ox - small * 0.3f, y + big + big * 0.1f + small, text)

        // Gear
        val gx = x + speedW + big * 0.5f
        val gy = y + big * 0.12f
        val gh = big * 0.62f
        line.strokeWidth = big * 0.03f
        c.drawRoundRect(RectF(gx, gy, gx + gearW, gy + gh + big * 0.3f), big * 0.06f, big * 0.06f, line)
        drawDigits(c, s.gear?.toString() ?: "", gx + (gearW - cellsWidth(1, gh * 0.75f)) / 2, gy + big * 0.08f, gh * 0.75f, 1)
        label(big * 0.13f, MID, Paint.Align.CENTER)
        c.drawText(if (s.gearless) "CVT" else "GEAR", gx + gearW / 2, gy + gh + big * 0.22f, text)
    }

    /** Four small vertical gauges: fuel, coolant, battery, intake. */
    private fun drawBars(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        fun temp(cel: Float?) = cel?.let { if (units == Units.MPH) (it * 9 / 5 + 32).roundToInt() else it.roundToInt() }?.toString()
        val gauges = listOf(
            Gauge("FUEL", s.fuelPct?.roundToInt()?.toString(), s.fuelPct?.div(100f), warn = (s.fuelPct ?: 100f) < 12.5f),
            Gauge("COOL", temp(s.coolantC), s.coolantC?.let { (it - 40f) / 80f }, warn = (s.coolantC ?: 0f) > 105f),
            Gauge("VOLT", s.volts?.let { "%.1f".format(it) }, s.volts?.let { (it - 10f) / 6f }, warn = s.volts != null && (s.volts < 11.8f || s.volts > 15.2f)),
            Gauge("AIR", temp(s.intakeC), s.intakeC?.let { (it + 10f) / 70f }),
        )
        val colW = b.width() / gauges.size
        gauges.forEachIndexed { i, g -> drawBar(c, RectF(b.left + i * colW, b.top, b.left + (i + 1) * colW, b.bottom), g) }
    }

    private data class Gauge(val label: String, val value: String?, val frac: Float?, val warn: Boolean = false)

    private fun drawBar(c: Canvas, b: RectF, g: Gauge) {
        val digitH = min(b.height() * 0.13f, b.width() * 0.32f)
        val labelH = digitH * 0.62f
        drawDigits(c, if (g.warn && !blinkOn) "" else g.value ?: "", b.centerX() - cellsWidth(3, digitH) / 2, b.top, digitH, 3)
        val top = b.top + digitH * 1.5f
        val bottom = b.bottom - labelH * 1.6f
        val n = 10
        val barW = min(b.width() * 0.36f, digitH * 1.5f)
        val step = (bottom - top) / n
        val x = b.centerX() - barW / 2
        val litCount = g.frac?.let { (it.coerceIn(0f, 1f) * n).roundToInt() } ?: 0
        for (i in 0 until n) {
            val y = bottom - (i + 1) * step + step * 0.14f
            val rect = RectF(x, y, x + barW, y + step * 0.72f)
            c.drawRect(rect, if (i < litCount && (!g.warn || blinkOn)) lit else ghost)
        }
        label(labelH, MID, Paint.Align.CENTER)
        c.drawText(g.label, b.centerX(), b.bottom, text)
    }

    /** Braking as a row of segments, 0 to 0.8 g, with the g value. */
    private fun drawBrake(c: Canvas, b: RectF, s: Snapshot) {
        val brake = s.g?.brake ?: 0f
        val labelH = b.height() * 0.3f
        label(labelH, MID, Paint.Align.LEFT)
        c.drawText("BRAKE", b.left, b.top + labelH, text)
        val digitH = b.height() * 0.42f
        drawDigits(c, if (s.g == null) "" else "%.2f".format(brake), b.right - cellsWidth(3, digitH), b.top, digitH, 3)
        val n = 16
        val top = b.top + b.height() * 0.58f
        val segH = b.bottom - top
        val step = b.width() / n
        val on = ((brake / 0.8f).coerceIn(0f, 1f) * n).roundToInt()
        for (i in 0 until n) {
            val x = b.left + i * step
            c.drawRect(x + step * 0.12f, top, x + step * 0.88f, top + segH, if (i < on) lit else ghost)
        }
    }

    /** The g-meter as a little round scope with a trail. */
    private fun drawScope(c: Canvas, b: RectF, s: Snapshot) {
        val cx = b.centerX()
        val cy = b.centerY()
        val rad = min(b.width(), b.height()) / 2f * 0.9f
        line.strokeWidth = rad * 0.02f
        line.color = GRID
        for (k in listOf(0.25f, 0.5f, 0.75f)) c.drawCircle(cx, cy, rad * k, line)
        c.drawLine(cx - rad, cy, cx + rad, cy, line)
        c.drawLine(cx, cy - rad, cx, cy + rad, line)
        line.color = MID
        c.drawCircle(cx, cy, rad, line)
        label(rad * 0.14f, MID, Paint.Align.LEFT)
        c.drawText("G", b.left, b.top + rad * 0.14f, text)
        if (!s.calibrated || s.calibrating) {
            if (blinkOn) {
                label(rad * 0.15f, GREEN, Paint.Align.CENTER)
                c.drawText(if (s.calibrating) "HOLD" else "CAL", cx, cy + rad * 0.45f, text)
            }
            return
        }
        fun px(g: CarG): Pair<Float, Float> {
            val k = 1.2f / maxOf(g.total, 1.2f)
            return (cx + g.lat * k * rad) to (cy - g.lon * k * rad)
        }
        val a = lit.alpha
        s.trail.forEachIndexed { i, g ->
            lit.alpha = 30 + 140 * i / maxOf(1, s.trail.size)
            val (x, y) = px(g)
            c.drawCircle(x, y, rad * 0.035f, lit)
        }
        lit.alpha = a
        val g = s.g ?: return
        val (x, y) = px(g)
        c.drawCircle(x, y, rad * 0.09f, lit)
    }

    /** The four tire pressures in small digits (front left, front right, rear left, rear right). Empty until the car can tell us. */
    private fun drawTires(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val cells = if (units == Units.MPH) 2 else 3
        val labelH = b.height() * 0.22f
        val cellW = b.width() * 0.21f
        val digitH = min(b.height() * 0.58f, cellW * 0.92f / (if (cells == 2) 1.32f else 2.08f))
        for (i in 0 until 4) {
            val x = b.left + i * cellW
            label(labelH, MID, Paint.Align.LEFT)
            c.drawText(Tpms.NAMES[i], x, b.top + labelH, text)
            drawDigits(c, Tpms.digits(s.tires.getOrNull(i), units), x, b.bottom - digitH, digitH, cells)
        }
        label(labelH, MID, Paint.Align.LEFT)
        c.drawText(Tpms.unit(units).uppercase(), b.left + 4 * cellW, b.bottom, text)
    }

    /** Odometer-style trip distance. */
    private fun drawTrip(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val digitH = min(b.height() * 0.62f, b.width() / 3.2f)
        val labelH = min(b.height() * 0.3f, digitH * 0.45f)
        label(labelH, MID, Paint.Align.LEFT)
        c.drawText("TRIP ${units.distanceLabel.uppercase()}", b.left, b.top + labelH, text)
        drawDigits(c, oneDecimal(units.distance(s.tripM)), b.left, b.bottom - digitH, digitH, 4)
    }

    /** Tell-tale lamps: lit when working, faint when not. REC blinks while recording. */
    private fun drawLamps(c: Canvas, b: RectF, s: Snapshot) {
        val lamps = listOf("GPS" to (s.accuracyM != null), "OBD" to s.obdLive, "REC" to (s.recording && blinkOn))
        val h = b.height()
        val w = min(b.width() / lamps.size * 0.86f, h * 2.6f)
        label(min(h * 0.58f, w * 0.24f), BG, Paint.Align.CENTER)
        val gap = (b.width() - w * lamps.size) / (lamps.size - 1).coerceAtLeast(1)
        line.strokeWidth = h * 0.07f
        lamps.forEachIndexed { i, (name, on) ->
            val x = b.right - (lamps.size - i) * w - (lamps.size - 1 - i) * gap
            val rect = RectF(x, b.top, x + w, b.bottom)
            if (on) {
                c.drawRoundRect(rect, h * 0.2f, h * 0.2f, lit)
                text.color = BG
            } else {
                line.color = GHOST_LINE
                c.drawRoundRect(rect, h * 0.2f, h * 0.2f, line)
                text.color = GHOST_LINE
            }
            c.drawText(name, rect.centerX(), rect.centerY() + text.textSize * 0.36f, text)
        }
        line.color = MID
    }

    // --- seven-segment digits -------------------------------------------------------------

    private fun cellW(h: Float) = h * 0.56f
    private fun cellGap(h: Float) = h * 0.2f
    fun cellsWidth(n: Int, h: Float) = n * cellW(h) + (n - 1) * cellGap(h)

    /**
     * Draws [value] right-aligned in [cells] seven-segment cells starting at ([x], [y]), [h]
     * tall. Every cell shows its unlit segments faintly. Digits, '-' and '.' only.
     */
    private fun drawDigits(c: Canvas, value: String, x: Float, y: Float, h: Float, cells: Int) {
        val chars = mutableListOf<Pair<Char, Boolean>>() // char, dot after it
        for (ch in value) {
            if (ch == '.' && chars.isNotEmpty()) chars[chars.size - 1] = chars.last().first to true else if (ch != '.') chars += ch to false
        }
        val n = maxOf(cells, chars.size)
        val first = n - chars.size // right-aligned
        for (i in 0 until n) {
            val cx = x + i * (cellW(h) + cellGap(h))
            drawCell(c, '8', cx, y, h, ghost, dot = false)
            val k = i - first
            if (k >= 0) {
                val (ch, dot) = chars[k]
                drawCell(c, ch, cx, y, h, lit, dot)
            }
        }
    }

    private fun drawCell(c: Canvas, ch: Char, x: Float, y: Float, h: Float, p: Paint, dot: Boolean) {
        val on = SEGMENTS[ch] ?: return
        val w = cellW(h)
        val t = h * 0.13f
        val g = h * 0.025f
        val slant = 0.1f // a slight italic lean, like old LCDs
        fun sx(px: Float, py: Float) = px + (y + h - py) * slant
        fun hor(yy: Float) {
            val x1 = x + t / 2 + g
            val x2 = x + w - t / 2 - g
            seg.reset()
            seg.moveTo(sx(x1, yy), yy)
            seg.lineTo(sx(x1 + t / 2, yy - t / 2), yy - t / 2)
            seg.lineTo(sx(x2 - t / 2, yy - t / 2), yy - t / 2)
            seg.lineTo(sx(x2, yy), yy)
            seg.lineTo(sx(x2 - t / 2, yy + t / 2), yy + t / 2)
            seg.lineTo(sx(x1 + t / 2, yy + t / 2), yy + t / 2)
            seg.close()
            c.drawPath(seg, p)
        }
        fun ver(xx: Float, y1: Float, y2: Float) {
            seg.reset()
            seg.moveTo(sx(xx, y1), y1)
            seg.lineTo(sx(xx + t / 2, y1 + t / 2), y1 + t / 2)
            seg.lineTo(sx(xx + t / 2, y2 - t / 2), y2 - t / 2)
            seg.lineTo(sx(xx, y2), y2)
            seg.lineTo(sx(xx - t / 2, y2 - t / 2), y2 - t / 2)
            seg.lineTo(sx(xx - t / 2, y1 + t / 2), y1 + t / 2)
            seg.close()
            c.drawPath(seg, p)
        }
        val mid = y + h / 2
        if ('a' in on) hor(y + t / 2)
        if ('g' in on) hor(mid)
        if ('d' in on) hor(y + h - t / 2)
        if ('f' in on) ver(x + t / 2, y + t / 2 + g, mid - g)
        if ('b' in on) ver(x + w - t / 2, y + t / 2 + g, mid - g)
        if ('e' in on) ver(x + t / 2, mid + g, y + h - t / 2 - g)
        if ('c' in on) ver(x + w - t / 2, mid + g, y + h - t / 2 - g)
        if (dot) c.drawCircle(sx(x + w + cellGap(h) * 0.5f, y + h - t / 2), y + h - t / 2, t * 0.55f, p)
    }

    private fun label(size: Float, color: Int, align: Paint.Align) {
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    companion object {
        /** Saab's instrument green. */
        val GREEN = Color.rgb(124, 240, 140)
        val MID = Color.rgb(70, 150, 86)
        val GRID = Color.rgb(28, 64, 38)
        val GHOST = Color.rgb(16, 34, 21)
        val GHOST_LINE = Color.rgb(34, 72, 44)
        val BG = Color.rgb(4, 8, 5)
        private val GLOW = Color.argb(150, 90, 230, 110)

        private val SEGMENTS = mapOf(
            '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
            '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg", '-' to "g",
        )
    }
}
