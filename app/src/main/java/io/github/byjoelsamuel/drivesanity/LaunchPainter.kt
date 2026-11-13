package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import io.github.byjoelsamuel.drivesanity.track.Phase
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The 0–60 screen: one big thing at a time, with room around it. It says READY when the car is stopped,
 * counts 3, 2, 1 and GO if the countdown is on, runs the clock as the car moves off, and shows the time
 * to 60 mph and to 100 km/h when it's done. A row of thin ticks fills towards 60 mph (or 100 km/h) as
 * the speed climbs.
 */
class LaunchPainter(context: Context) {
    private val app = context.applicationContext
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = labels; text.letterSpacing = 0.1f; text.textSize = size; text.color = color; text.textAlign = align
    }

    private fun number(size: Float, color: Int = PALE, align: Paint.Align = Paint.Align.CENTER) {
        text.typeface = numbers; text.letterSpacing = 0f; text.textSize = size; text.color = color; text.textAlign = align
    }

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        fill.color = Colors.BG
        c.drawRect(r, fill)
        val h = r.height()
        val w = r.width()
        val left = r.left + w * 0.07f
        val right = r.right - w * 0.07f
        val cx = r.centerX()
        val metric = units != Units.MPH
        val targetMps = if (metric) 100f / 3.6f else 60f * 0.44704f
        val l = s.launch
        val countdownOn = Prefs.launchCountdown(app)

        // The title, and whether the countdown is on.
        label(h * 0.06f)
        c.drawText(if (metric) "0–100 KM/H" else "0–60 MPH", left, r.top + h * 0.15f, text)
        label(h * 0.045f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText(if (countdownOn) "COUNTDOWN ON" else "COUNTDOWN OFF", right, r.top + h * 0.15f, text)

        // The big thing and the line under it.
        val (big, sub) = when {
            l == null || s.speedMps == null -> Triple("–", Colors.DIM, h * 0.3f) to "Waiting for the car's speed"
            else -> when (l.phase) {
                Phase.READY -> Triple("READY", Colors.GOOD, h * 0.3f) to if (countdownOn) "Press Start 3-2-1, then go" else "Floor it when you're ready"
                Phase.COUNTDOWN -> {
                    val col = when (l.count) { 3 -> Colors.SAND; 2 -> Colors.CLAY; else -> Colors.RED }
                    Triple((l.count ?: 3).toString(), col, h * (0.42f - 0.08f * l.frac)) to "Get ready"
                }
                Phase.GO -> Triple("GO", Colors.GOOD, h * 0.4f) to ""
                Phase.RUNNING -> Triple(secs(l.liveMs), PALE, h * 0.36f) to "${units.speed(s.speedMps).roundToInt()} ${units.label}"
                Phase.DONE -> {
                    val t = if (metric) l.to100Ms else l.to60Ms
                    Triple(if (t != null) secs(t) else "–", if (t != null) Colors.GOOD else Colors.DIM, h * 0.36f) to
                        (if (t != null) "${if (metric) "0–100 km/h" else "0–60 mph"}, done" else "Didn't get to ${if (metric) "100 km/h" else "60 mph"}")
                }
                Phase.MOVING -> Triple("STOP TO ARM", Colors.DIM, h * 0.17f) to "Come to a stop, then go"
                Phase.TOO_EARLY -> Triple("TOO EARLY", Colors.CLAY, h * 0.22f) to "Wait for the GO"
            }
        }
        val (text0, color, size) = big
        number(size, color)
        c.drawText(text0, cx, r.top + h * 0.47f, text)
        if (sub.isNotEmpty()) {
            label(h * 0.055f, Colors.DIM, Paint.Align.CENTER)
            c.drawText(sub, cx, r.top + h * 0.575f, text)
        }

        // Speed towards the target, as thin ticks.
        val f = ((s.speedMps ?: 0f) / targetMps).coerceIn(0f, 1f)
        val n = 60
        val barL = left
        val barR = right
        val pitch = (barR - barL) / n
        val top = r.top + h * 0.645f
        val bottom = r.top + h * 0.705f
        for (i in 0 until n) {
            val part = (f * n - i).coerceIn(0f, 1f)
            val on = Colors.mix(Colors.GOOD, Colors.SAND, i / n.toFloat())
            fill.color = if (part >= 0.98f) on else if (part > 0.02f) Colors.mix(Colors.GRID, on, part) else Colors.GRID
            val x = barL + i * pitch
            c.drawRect(x + pitch * 0.2f, top, x + pitch * 0.8f, bottom, fill)
        }
        label(h * 0.045f)
        c.drawText("0", barL, r.top + h * 0.775f, text)
        label(h * 0.045f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText(if (metric) "100 km/h" else "60 mph", barR, r.top + h * 0.775f, text)

        // The last run, both ways.
        if (l?.hasResult == true) {
            val half = (right - left) / 2
            result(c, left, r.top + h * 0.955f, h, "0–60 MPH", l.to60Ms)
            result(c, left + half, r.top + h * 0.955f, h, "0–100 KM/H", l.to100Ms)
        }
    }

    private fun result(c: Canvas, x: Float, y: Float, h: Float, name: String, ms: Long?) {
        label(h * 0.045f)
        c.drawText(name, x, y - h * 0.095f, text)
        number(h * 0.075f, if (ms == null) Colors.DIM else PALE, Paint.Align.LEFT)
        c.drawText(if (ms == null) "–" else secs(ms), x, y, text)
    }

    private fun secs(ms: Long?): String = if (ms == null) "–" else "%.2f s".format(java.util.Locale.US, ms / 1000.0)

    private companion object {
        val PALE = android.graphics.Color.rgb(222, 228, 236)
    }
}
