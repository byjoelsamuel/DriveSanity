package io.github.byjoelsamuel.drivesanity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min
import kotlin.math.roundToInt

object Colors {
    val BG = Color.rgb(14, 17, 20)
    val FG = Color.rgb(232, 234, 237)
    val DIM = Color.rgb(110, 118, 126)
    val GRID = Color.rgb(48, 54, 60)
    val ACCEL = Color.rgb(79, 195, 247)
    val REC = Color.rgb(244, 67, 54)
    val WHITE = Color.rgb(245, 245, 245)
    val YELLOW = Color.rgb(255, 202, 40)
    val RED = Color.rgb(244, 54, 54)
    val RED_DIM = Color.rgb(84, 34, 36)

    fun brake(level: BrakeLevel) = when (level) {
        BrakeLevel.NONE -> DIM
        BrakeLevel.LIGHT -> Color.rgb(139, 195, 74)
        BrakeLevel.MODERATE -> Color.rgb(255, 193, 7)
        BrakeLevel.HARD -> Color.rgb(255, 112, 67)
        BrakeLevel.VERY_HARD -> Color.rgb(244, 67, 54)
    }

    /** White, fading to yellow, then red at the redline. */
    fun rpm(rpm: Float): Int {
        val h = Revs.heat(rpm)
        return if (h <= 1f) mix(WHITE, YELLOW, h) else mix(YELLOW, RED, h - 1f)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return Color.rgb(
            ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)),
            ch(Color.blue(a), Color.blue(b)),
        )
    }
}

/**
 * Draws the dashboard; the phone and the car screen both use it. Every size comes from the
 * space it's given, so it fits any screen.
 *
 * Wide screens (the car, a phone on its side): braking, a big speed and the RPM bar stacked
 * on the left, a small g-meter on the right. Tall screens: the same stack with the g-meter
 * underneath.
 */
class DashPainter {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        fill.color = Colors.BG
        c.drawRect(r, fill)
        if (r.width() > r.height() * 1.2f) drawWide(c, r, s, units) else drawTall(c, r, s, units)
    }

    private fun drawWide(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val pad = r.height() * 0.05f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val split = inner.left + inner.width() * 0.68f
        val h = inner.height()
        var y = inner.top
        fun row(frac: Float) = RectF(inner.left, y, split - pad, y + h * frac).also { y += h * frac }
        drawStatus(c, row(0.07f), s)
        drawBrake(c, row(0.18f), s)
        drawSpeed(c, row(0.53f), s, units)
        drawRpm(c, row(0.22f), s)

        val size = min(inner.right - split, h * 0.80f)
        val cx = (split + inner.right) / 2f
        drawGMeter(c, RectF(cx - size / 2, inner.centerY() - size / 2, cx + size / 2, inner.centerY() + size / 2), s)
    }

    private fun drawTall(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val pad = r.width() * 0.05f
        val inner = RectF(r.left + pad, r.top + pad * 0.5f, r.right - pad, r.bottom - pad * 0.5f)
        val h = inner.height()
        var y = inner.top
        fun row(frac: Float) = RectF(inner.left, y, inner.right, y + h * frac).also { y += h * frac }
        drawStatus(c, row(0.035f), s)
        drawBrake(c, row(0.11f), s)
        drawSpeed(c, row(0.26f), s, units)
        drawRpm(c, row(0.12f), s)
        row(0.025f)
        drawGMeter(c, row(1f - 0.035f - 0.11f - 0.26f - 0.12f - 0.025f), s)
    }

    /** GPS state on the left, REC on the right. */
    private fun drawStatus(c: Canvas, b: RectF, s: Snapshot) {
        text.textSize = min(b.height() * 0.8f, b.width() * 0.045f)
        text.textAlign = Paint.Align.LEFT
        text.color = if (s.accuracyM != null) Colors.DIM else Colors.REC
        val gps = when {
            !s.gpsOn -> "NO GPS ACCESS"
            s.accuracyM == null -> "WAITING FOR GPS"
            else -> "GPS ±${s.accuracyM.roundToInt()} m"
        }
        c.drawText(gps, b.left, b.bottom - b.height() * 0.2f, text)
        if (s.recording) {
            text.textAlign = Paint.Align.RIGHT
            text.color = Colors.REC
            c.drawText("● REC", b.right, b.bottom - b.height() * 0.2f, text)
        }
    }

    /** The big number in the main units, with the other units small underneath. */
    private fun drawSpeed(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val big = min(b.height() * 0.80f, b.width() * 0.32f)
        val unitSize = big * 0.22f
        val small = big * 0.20f
        val base = b.top + big * 0.80f

        // No fix yet: a smaller dash, so it doesn't read as a giant number.
        val speed = s.speedMps?.let { units.speed(it).roundToInt().toString() } ?: "--"
        val numSize = if (s.speedMps == null) big * 0.5f else big
        text.textAlign = Paint.Align.LEFT
        text.textSize = numSize
        val numW = text.measureText(speed)
        text.textSize = unitSize
        val gap = big * 0.06f
        val unitW = text.measureText(units.label)
        val x = b.centerX() - (numW + gap + unitW) / 2f

        text.textSize = numSize
        text.color = if (s.speedMps == null) Colors.DIM else Colors.FG
        c.drawText(speed, x, base, text)
        text.textSize = unitSize
        text.color = Colors.DIM
        c.drawText(units.label, x + numW + gap, base, text)

        val other = units.other
        val second = s.speedMps?.let { other.speed(it).roundToInt().toString() } ?: "--"
        text.textAlign = Paint.Align.CENTER
        text.textSize = small
        c.drawText("$second ${other.label}", b.centerX(), base + small * 1.35f, text)
    }

    private fun drawBrake(c: Canvas, b: RectF, s: Snapshot) {
        val brake = s.g?.brake ?: 0f
        val level = BrakeLevel.of(brake)
        val size = min(b.height() * 0.36f, b.width() * 0.07f)

        text.textSize = size * 0.7f
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("BRAKE", b.left, b.top + size, text)

        text.textSize = size
        text.textAlign = Paint.Align.RIGHT
        text.color = Colors.brake(level)
        val readout = when {
            s.g == null -> "--"
            level == BrakeLevel.NONE -> "NONE"
            else -> "${level.label}  %.2f g".format(brake)
        }
        c.drawText(readout, b.right, b.top + size, text)

        // The bar runs 0 to 0.8 g; few road cars brake much harder than that.
        val barTop = b.top + size * 1.45f
        val barH = min(size * 0.8f, b.bottom - barTop - size * 0.15f)
        if (barH <= 0f) return
        val track = RectF(b.left, barTop, b.right, barTop + barH)
        fill.color = Colors.GRID
        c.drawRoundRect(track, barH / 2, barH / 2, fill)
        val frac = (brake / 0.8f).coerceIn(0f, 1f)
        if (frac > 0f) {
            fill.color = Colors.brake(level)
            c.drawRoundRect(RectF(b.left, barTop, b.left + track.width() * frac, track.bottom), barH / 2, barH / 2, fill)
        }
        stroke.color = Colors.BG
        stroke.strokeWidth = barH * 0.08f
        for (l in BrakeLevel.entries.drop(1)) {
            val x = b.left + track.width() * (l.from / 0.8f)
            c.drawLine(x, barTop, x, track.bottom, stroke)
        }
    }

    /**
     * A segmented bar, 0 to [Revs.MAX], one segment per 250 rpm. Lit segments go white, then
     * yellow, then red from the redline; unlit ones past the redline stay dark red.
     */
    private fun drawRpm(c: Canvas, b: RectF, s: Snapshot) {
        val rpm = s.rpm
        val size = min(b.height() * 0.30f, b.width() * 0.07f)

        text.textSize = size * 0.7f
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("RPM", b.left, b.top + size, text)
        text.textAlign = Paint.Align.RIGHT
        if (rpm == null) {
            c.drawText("-- · no OBD2 yet", b.right, b.top + size, text)
        } else {
            text.textSize = size
            text.color = Colors.rpm(rpm)
            c.drawText("%,d".format((rpm / 50f).roundToInt() * 50), b.right, b.top + size, text)
        }

        val labelSize = size * 0.55f
        val barTop = b.top + size * 1.4f
        val barH = min(size * 1.1f, b.bottom - barTop - labelSize * 1.3f)
        if (barH <= 0f) return
        val step = 250f
        val n = (Revs.MAX / step).toInt()
        val segW = b.width() / n
        val gap = segW * 0.22f
        for (i in 0 until n) {
            val from = i * step
            val lit = rpm != null && rpm > from
            fill.color = when {
                lit -> Colors.rpm(from + step / 2)
                from + step > Revs.REDLINE -> Colors.RED_DIM
                else -> Colors.GRID
            }
            val x0 = b.left + i * segW + gap / 2
            c.drawRect(x0, barTop, x0 + segW - gap, barTop + barH, fill)
        }

        text.textSize = labelSize
        text.textAlign = Paint.Align.CENTER
        for (k in 0..(Revs.MAX / 1000f).toInt()) {
            val x = (b.left + k * 1000f / Revs.MAX * b.width()).coerceIn(b.left + labelSize * 0.3f, b.right - labelSize * 0.3f)
            text.color = if (k * 1000f >= Revs.REDLINE) Colors.RED else Colors.DIM
            c.drawText(k.toString(), x, barTop + barH + labelSize * 1.15f, text)
        }
    }

    private fun drawGMeter(c: Canvas, b: RectF, s: Snapshot) {
        val cx = b.centerX()
        val cy = b.centerY()
        val radius = min(b.width(), b.height()) / 2f * 0.80f   // 1.0 g
        val label = radius * 0.13f

        stroke.strokeWidth = radius * 0.014f
        for (ring in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            stroke.color = if (ring == 0.5f || ring == 1f) Colors.DIM else Colors.GRID
            c.drawCircle(cx, cy, radius * ring, stroke)
        }
        stroke.color = Colors.GRID
        c.drawLine(cx - radius, cy, cx + radius, cy, stroke)
        c.drawLine(cx, cy - radius, cx, cy + radius, stroke)

        text.textSize = label
        text.color = Colors.DIM
        text.textAlign = Paint.Align.CENTER
        c.drawText("ACCEL", cx, cy - radius - label * 0.4f, text)
        c.drawText("BRAKE", cx, cy + radius + label * 1.1f, text)

        if (!s.calibrated || s.calibrating) {
            text.textSize = label * 1.05f
            val lines = if (s.calibrating) listOf("Hold still…") else listOf("Stop the car,", "tap CALIBRATE")
            val w = lines.maxOf { text.measureText(it) } / 2f + label * 0.5f
            val top = cy - radius * 0.08f - label * 1.2f
            fill.color = Colors.BG
            c.drawRoundRect(cx - w, top, cx + w, top + label * (0.6f + 1.5f * lines.size), label * 0.4f, label * 0.4f, fill)
            text.color = Colors.FG
            lines.forEachIndexed { i, line -> c.drawText(line, cx, cy - radius * 0.08f + i * label * 1.5f, text) }
            return
        }

        fun px(g: CarG): Pair<Float, Float> {
            // Clamp to 1.2 g so a pothole doesn't throw the dot off screen.
            val scale = 1.2f / maxOf(g.total, 1.2f)
            return (cx + g.lat * scale * radius) to (cy - g.lon * scale * radius)
        }

        fill.color = Colors.ACCEL
        s.trail.forEachIndexed { i, g ->
            fill.alpha = 30 + 150 * i / maxOf(1, s.trail.size)
            val (x, y) = px(g)
            c.drawCircle(x, y, radius * 0.03f, fill)
        }
        fill.alpha = 255

        val g = s.g ?: return
        val level = BrakeLevel.of(g.brake)
        fill.color = if (level == BrakeLevel.NONE) Colors.ACCEL else Colors.brake(level)
        val (x, y) = px(g)
        c.drawCircle(x, y, radius * 0.09f, fill)

        text.textSize = label * 1.1f
        text.color = Colors.FG
        text.textAlign = Paint.Align.LEFT
        c.drawText("%.2f g".format(g.total), b.left, b.top + label * 1.1f, text)
    }
}

/** Rounds to one decimal without dragging in a formatter per frame. */
internal fun oneDecimal(v: Double) = ((v * 10).roundToInt() / 10.0).toString()

internal fun hms(sec: Double): String {
    val t = sec.toLong()
    return "%d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60)
}
