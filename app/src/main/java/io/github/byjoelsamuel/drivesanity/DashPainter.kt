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

    fun brake(level: BrakeLevel) = when (level) {
        BrakeLevel.NONE -> DIM
        BrakeLevel.LIGHT -> Color.rgb(139, 195, 74)
        BrakeLevel.MODERATE -> Color.rgb(255, 193, 7)
        BrakeLevel.HARD -> Color.rgb(255, 112, 67)
        BrakeLevel.VERY_HARD -> Color.rgb(244, 67, 54)
    }
}

/**
 * Draws the dashboard: speed, the g-force circle and the braking bar. The phone and the car
 * screen both use it. Every size comes from the space it's given, so it fits any screen.
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
        if (r.width() > r.height() * 1.2f) {
            val pad = r.height() * 0.05f
            val split = r.left + r.width() * 0.46f
            val mid = r.top + r.height() * 0.60f
            drawSpeed(c, RectF(r.left + pad, r.top + pad, split - pad, mid), s, units)
            drawBrake(c, RectF(r.left + pad, mid, split - pad, r.bottom - pad), s)
            drawCircle(c, RectF(split, r.top + pad, r.right - pad, r.bottom - pad), s)
        } else {
            val pad = r.width() * 0.05f
            val h = r.height()
            drawSpeed(c, RectF(r.left + pad, r.top + pad, r.right - pad, r.top + h * 0.28f), s, units)
            drawCircle(c, RectF(r.left + pad, r.top + h * 0.28f, r.right - pad, r.top + h * 0.80f), s)
            drawBrake(c, RectF(r.left + pad, r.top + h * 0.80f, r.right - pad, r.bottom - pad), s)
        }
    }

    private fun drawSpeed(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val small = min(b.height() * 0.13f, b.width() * 0.07f)
        val big = min(b.height() * 0.60f, b.width() * 0.42f)

        text.textSize = small
        text.textAlign = Paint.Align.LEFT
        text.color = if (s.accuracyM != null) Colors.DIM else Colors.REC
        val gps = when {
            !s.gpsOn -> "NO GPS ACCESS"
            s.accuracyM == null -> "WAITING FOR GPS"
            else -> "GPS ±${s.accuracyM.roundToInt()} m"
        }
        c.drawText(gps, b.left, b.top + small, text)
        if (s.recording) {
            text.textAlign = Paint.Align.RIGHT
            text.color = Colors.REC
            c.drawText("● REC", b.right, b.top + small, text)
        }

        val numberBase = b.top + small * 1.5f + big * 0.85f
        text.textAlign = Paint.Align.CENTER
        text.color = Colors.FG
        text.textSize = big
        val speed = s.speedMps?.let { units.speed(it).roundToInt().toString() } ?: "--"
        c.drawText(speed, b.centerX(), numberBase, text)
        text.textSize = small
        text.color = Colors.DIM
        c.drawText(units.label, b.centerX(), numberBase + small * 1.4f, text)
    }

    private fun drawCircle(c: Canvas, b: RectF, s: Snapshot) {
        val cx = b.centerX()
        val cy = b.centerY()
        val radius = min(b.width(), b.height()) / 2f * 0.84f   // 1.0 g
        val label = radius * 0.10f

        stroke.strokeWidth = radius * 0.012f
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
        c.drawText("L", cx - radius - label * 0.8f, cy + label * 0.35f, text)
        c.drawText("R", cx + radius + label * 0.8f, cy + label * 0.35f, text)
        text.textSize = label * 0.8f
        text.textAlign = Paint.Align.LEFT
        c.drawText("0.5g", cx + radius * 0.37f, cy + radius * 0.37f + label, text)
        c.drawText("1g", cx + radius * 0.72f, cy + radius * 0.72f + label, text)

        if (!s.calibrated || s.calibrating) {
            text.textSize = label * 1.2f
            text.color = Colors.FG
            text.textAlign = Paint.Align.CENTER
            val lines = if (s.calibrating) listOf("Hold still…") else listOf("Stop the car,", "then tap CALIBRATE")
            lines.forEachIndexed { i, line -> c.drawText(line, cx, cy - radius * 0.12f + i * label * 1.6f, text) }
            return
        }

        fun px(g: CarG): Pair<Float, Float> {
            // Clamp to 1.2 g so a pothole doesn't throw the dot off screen.
            val scale = (1.2f / maxOf(g.total, 1.2f))
            return (cx + g.lat * scale * radius) to (cy - g.lon * scale * radius)
        }

        fill.color = Colors.ACCEL
        s.trail.forEachIndexed { i, g ->
            fill.alpha = 30 + 150 * i / maxOf(1, s.trail.size)
            val (x, y) = px(g)
            c.drawCircle(x, y, radius * 0.025f, fill)
        }
        fill.alpha = 255

        val g = s.g ?: return
        val level = BrakeLevel.of(g.brake)
        fill.color = if (level == BrakeLevel.NONE) Colors.ACCEL else Colors.brake(level)
        val (x, y) = px(g)
        c.drawCircle(x, y, radius * 0.07f, fill)

        text.textSize = label * 1.3f
        text.color = Colors.FG
        text.textAlign = Paint.Align.LEFT
        c.drawText("%.2f g".format(g.total), b.left, b.top + label * 1.3f, text)
    }

    private fun drawBrake(c: Canvas, b: RectF, s: Snapshot) {
        val brake = s.g?.brake ?: 0f
        val level = BrakeLevel.of(brake)
        val size = min(b.height() * 0.30f, b.width() * 0.085f)

        text.textSize = size * 0.7f
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("BRAKING", b.left, b.top + size, text)

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
        val barTop = b.top + size * 1.6f
        val barH = min(size * 0.9f, b.bottom - barTop)
        if (barH <= 0f) return
        val track = RectF(b.left, barTop, b.right, barTop + barH)
        fill.color = Colors.GRID
        c.drawRoundRect(track, barH / 2, barH / 2, fill)
        val frac = (brake / 0.8f).coerceIn(0f, 1f)
        if (frac > 0f) {
            fill.color = Colors.brake(level)
            c.drawRoundRect(RectF(b.left, barTop, b.left + track.width() * frac, barTop + barH), barH / 2, barH / 2, fill)
        }
        stroke.color = Colors.BG
        stroke.strokeWidth = barH * 0.08f
        for (l in BrakeLevel.entries.drop(1)) {
            val x = b.left + track.width() * (l.from / 0.8f)
            c.drawLine(x, barTop, x, barTop + barH, stroke)
        }
    }
}

/** Rounds to one decimal without dragging in a formatter per frame. */
internal fun oneDecimal(v: Double) = ((v * 10).roundToInt() / 10.0).toString()

internal fun hms(sec: Double): String {
    val t = sec.toLong()
    return "%d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60)
}
