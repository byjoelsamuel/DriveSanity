package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.track.deltaText
import io.github.byjoelsamuel.drivesanity.track.lapText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Sport screen, laid out like a race car's display and set up for driving it hard.
 *
 *  - A long rev ribbon across the top: it fills left to right, turns sand and then red towards the
 *    redline, holds the highest revs of the last moment as a thin marker, and flashes at the shift point.
 *  - In the middle the gear and the speed, as big as they'll go.
 *  - On the left two tall pedal bars, throttle and brake, like the pedal traces on a racing display.
 *  - On the right a g-g plot: a dot for the car's g-force with a trail, and a tick where each of the
 *    hardest accelerations, brakings and corners has been.
 *  - Along the bottom the lap timer with a delta bar against the best lap, or the standing-start
 *    times, or air/fuel, load, coolant and the top speed when neither is on.
 */
class SportPainter(context: Context) {
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val speedRoll = RollingNumber()

    // Remembered between frames: the revs peak marker, and the hardest g in each direction.
    private var peakRpm = 0f
    private var peakFrame = 0L
    private var gUp = 0f
    private var gDown = 0f
    private var gLeft = 0f
    private var gRight = 0f
    private var lastNs = 0L

    private fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = labels
        text.letterSpacing = 0.06f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    private fun number(size: Float, color: Int = PALE, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = numbers
        text.letterSpacing = 0f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.033f else ((now - lastNs) / 1e9f).coerceIn(0.001f, 0.25f)
        lastNs = now
        fill.shader = null
        fill.color = Colors.BG
        c.drawRect(r, fill)

        val pad = r.height() * 0.035f
        val b = RectF(r.left + pad, r.top + pad, r.right + -pad, r.bottom - pad)
        val h = b.height()
        val w = b.width()

        ribbon(c, RectF(b.left, b.top, b.right, b.top + h * 0.2f), s, dt)
        val mid = RectF(b.left, b.top + h * 0.23f, b.right, b.top + h * 0.77f)
        pedals(c, RectF(mid.left, mid.top, mid.left + w * 0.25f, mid.bottom), s)
        gearAndSpeed(c, RectF(mid.left + w * 0.28f, mid.top, mid.left + w * 0.69f, mid.bottom), s, units)
        gg(c, RectF(mid.left + w * 0.72f, mid.top, mid.right, mid.bottom), s, dt)
        strip(c, RectF(b.left, b.top + h * 0.81f, b.right, b.bottom), s, units)
    }

    // ---------------------------------------------------------------------------------------
    // The rev ribbon
    // ---------------------------------------------------------------------------------------

    private fun ribbon(c: Canvas, b: RectF, s: Snapshot, dt: Float) {
        val h = b.height()
        val rpm = s.rpm
        val level = Alerts.shiftLevel(rpm, Revs.REDLINE)
        val flash = level == 2 && (Alerts.now() / 100) % 2 == 0L

        // The number, at the right.
        val numW = b.width() * 0.17f
        label(h * 0.2f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText("RPM", b.right, b.top + h * 0.2f, text)
        number(h * 0.62f, if (flash) Colors.RED else if (rpm == null) Colors.DIM else PALE, Paint.Align.RIGHT)
        c.drawText(rpm?.let { "%,d".format((it / 10f).roundToInt() * 10) } ?: if (s.obdLive) "–" else "no OBD2", b.right, b.top + h * 0.78f, text)

        val x0 = b.left
        val x1 = b.right - numW - b.width() * 0.02f
        val barTop = b.top + h * 0.06f
        val barH = h * 0.36f
        val bar = RectF(x0, barTop, x1, barTop + barH)
        val round = barH / 2

        // The track, with the red zone tinted.
        fill.shader = null
        fill.color = Colors.GRID
        c.drawRoundRect(bar, round, round, fill)
        val redAt = (Revs.REDLINE / Revs.MAX).coerceIn(0.5f, 0.99f)
        val redLeft = x0 + (x1 - x0) * redAt
        c.save()
        c.clipRect(redLeft, bar.top, bar.right, bar.bottom)
        fill.color = Colors.RED_DIM
        c.drawRoundRect(bar, round, round, fill)
        c.restore()

        // The fill, coloured by where it is on the scale, so the colour is the same wherever the revs sit.
        if (rpm != null) {
            val f = (rpm / Revs.MAX).coerceIn(0f, 1f)
            val end = x0 + (x1 - x0) * f
            if (end - x0 > round) {
                fill.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(Colors.GOOD, Colors.GOOD, Colors.SAND, Colors.CLAY, Colors.RED),
                    floatArrayOf(0f, redAt * 0.55f, redAt * 0.82f, redAt, 1f),
                    Shader.TileMode.CLAMP,
                )
                c.drawRoundRect(RectF(x0, bar.top, end, bar.bottom), round, round, fill)
                fill.shader = null
            }
            if (flash) {
                fill.color = Colors.RED
                fill.alpha = 90
                c.drawRoundRect(bar, round, round, fill)
                fill.alpha = 255
            }
        }

        // The peak marker: stays where the revs got to, then slowly falls back.
        val nowMs = SystemClock.uptimeMillis()
        if (rpm != null && rpm >= peakRpm) {
            peakRpm = rpm
            peakFrame = nowMs
        } else if (nowMs - peakFrame > 1_200) {
            peakRpm = max(rpm ?: 0f, peakRpm - Revs.MAX * 0.35f * dt)
        }
        if (peakRpm > 0f && rpm != null) {
            val px = x0 + (x1 - x0) * (peakRpm / Revs.MAX).coerceIn(0f, 1f)
            fill.color = PALE
            c.drawRoundRect(RectF(px - 2.5f, bar.top - h * 0.05f, px + 2.5f, bar.bottom + h * 0.05f), 2f, 2f, fill)
        }

        // Thousands underneath.
        label(h * 0.2f, Colors.DIM, Paint.Align.CENTER)
        for (k in 0..(Revs.MAX / 1000f).toInt()) {
            val x = (x0 + (x1 - x0) * (k * 1000f / Revs.MAX)).coerceIn(x0 + h * 0.08f, x1 - h * 0.08f)
            text.color = if (k * 1000f >= Revs.REDLINE) Colors.RED else Colors.DIM
            c.drawText(k.toString(), x, bar.bottom + h * 0.4f, text)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Pedals
    // ---------------------------------------------------------------------------------------

    private fun pedals(c: Canvas, b: RectF, s: Snapshot) {
        val h = b.height()
        val barW = b.width() * 0.26f
        val gap = b.width() * 0.16f
        val left = b.left + (b.width() - 2 * barW - gap) / 2
        val thr = s.throttlePct
        val g = s.g
        val brake = g?.brake ?: 0f
        val brakeLevel = BrakeLevel.of(brake)
        pedal(c, RectF(left, b.top, left + barW, b.bottom), "THR", thr?.let { "${it.roundToInt()}%" } ?: "–", thr?.div(100f),
            intArrayOf(Colors.GOOD, Colors.SAND, Colors.CLAY))
        pedal(c, RectF(left + barW + gap, b.top, left + 2 * barW + gap, b.bottom), "BRK", if (g == null) "–" else "%.2f g".format(brake),
            if (g == null) null else (brake / 0.9f).coerceIn(0f, 1f),
            intArrayOf(Colors.SAND, Colors.CLAY, if (brakeLevel == BrakeLevel.NONE) Colors.RED else Colors.brake(brakeLevel)))
    }

    private fun pedal(c: Canvas, b: RectF, name: String, value: String, frac: Float?, colors: IntArray) {
        val h = b.height()
        label(h * 0.095f, Colors.DIM, Paint.Align.CENTER)
        c.drawText(name, b.centerX(), b.top + h * 0.09f, text)
        val bar = RectF(b.left, b.top + h * 0.14f, b.right, b.bottom - h * 0.19f)
        val round = b.width() * 0.28f
        fill.shader = null
        fill.color = Colors.GRID
        c.drawRoundRect(bar, round, round, fill)
        val f = (frac ?: 0f).coerceIn(0f, 1f)
        if (f > 0f) {
            val top = bar.bottom - bar.height() * f
            fill.shader = LinearGradient(0f, bar.bottom, 0f, bar.top, colors, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            c.save()
            c.clipRect(bar.left, top, bar.right, bar.bottom)
            c.drawRoundRect(bar, round, round, fill)
            c.restore()
            fill.shader = null
        }
        // Quarter marks.
        fill.color = Colors.BG
        for (q in 1..3) c.drawRect(bar.left, bar.top + bar.height() * q / 4f - 1.5f, bar.right, bar.top + bar.height() * q / 4f + 1.5f, fill)
        number(h * 0.13f, if (frac == null) Colors.DIM else PALE, Paint.Align.CENTER)
        c.drawText(value, b.centerX(), b.bottom - h * 0.03f, text)
    }

    // ---------------------------------------------------------------------------------------
    // Gear and speed
    // ---------------------------------------------------------------------------------------

    private fun gearAndSpeed(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val level = Alerts.shiftLevel(s.rpm, Revs.REDLINE)
        val flash = (Alerts.now() / 110) % 2 == 0L
        val gearColor = when {
            s.gearless -> Colors.DIM
            s.gear == null -> Colors.GRID
            level == 2 -> if (flash) Colors.RED else Colors.GRID
            level == 1 -> Colors.SAND
            else -> PALE
        }
        val gx = b.left + b.width() * 0.2f
        number(h * (if (s.gear == null) 0.55f else 1.22f), gearColor, Paint.Align.CENTER)
        c.drawText(s.gearText, gx, b.top + h * 0.86f, text)
        label(h * 0.1f, Colors.DIM, Paint.Align.CENTER)
        c.drawText(if (s.gearless) "NO FIXED GEARS" else "GEAR", gx, b.bottom - h * 0.01f, text)

        // The speed, as rolling digits, with its units.
        val shown = speedRoll.update(s.speedMps?.let { units.speed(it).roundToInt().toFloat() })
        val sx = b.left + b.width() * 0.64f
        val size = h * 0.66f
        number(size)
        if (shown == null) {
            number(size * 0.5f, Colors.DIM, Paint.Align.CENTER)
            c.drawText("–", sx, b.top + h * 0.7f, text)
        } else {
            val color = if (overLimit(s, units)) Colors.CLAY else PALE
            speedRoll.draw(c, text, shown, speedRoll.rate, sx, b.top + h * 0.7f, size, color)
        }
        label(h * 0.13f, Colors.DIM, Paint.Align.CENTER)
        c.drawText("${units.label.uppercase()}   ${s.speedMps?.let { units.other.speed(it).roundToInt() } ?: "–"} ${units.other.label}", sx, b.top + h * 0.9f, text)
    }

    private fun overLimit(s: Snapshot, units: Units): Boolean {
        val mps = s.speedMps ?: return false
        val l = io.github.byjoelsamuel.drivesanity.nav.SpeedLimit.current ?: return false
        return if (units == Units.MPH) units.speed(mps) > l.inMph() + 5 else units.speed(mps) > l.inKmh() + 8
    }

    // ---------------------------------------------------------------------------------------
    // g-g plot
    // ---------------------------------------------------------------------------------------

    private fun gg(c: Canvas, b: RectF, s: Snapshot, dt: Float) {
        val h = b.height()
        val side = min(b.width() * 0.62f, h * 0.96f)
        val plot = RectF(b.right - side, b.centerY() - side / 2, b.right, b.centerY() + side / 2)
        val scale = 1.0f // the edge of the plot is 1.0 g
        val cx = plot.centerX()
        val cy = plot.centerY()
        val half = side / 2
        val corner = side * 0.12f

        fill.shader = null
        fill.color = Colors.BOX
        c.drawRoundRect(plot, corner, corner, fill)
        // Rings at half a g and a g, as rounded squares, and the axes.
        stroke.shader = null
        stroke.strokeWidth = max(1.5f, side * 0.01f)
        stroke.color = Colors.GRID
        c.drawRoundRect(RectF(cx - half * 0.5f, cy - half * 0.5f, cx + half * 0.5f, cy + half * 0.5f), corner * 0.6f, corner * 0.6f, stroke)
        c.drawLine(plot.left + corner * 0.5f, cy, plot.right - corner * 0.5f, cy, stroke)
        c.drawLine(cx, plot.top + corner * 0.5f, cx, plot.bottom - corner * 0.5f, stroke)
        label(side * 0.07f, Colors.DIM, Paint.Align.CENTER)
        c.drawText("ACCEL", cx, plot.top + side * 0.1f, text)
        c.drawText("BRAKE", cx, plot.bottom - side * 0.04f, text)

        fun px(g: CarG) = cx + (g.lat / scale).coerceIn(-1f, 1f) * half * 0.9f
        fun py(g: CarG) = cy - (g.lon / scale).coerceIn(-1f, 1f) * half * 0.9f

        // The hardest g so far in each direction, slowly forgotten.
        val g = s.g
        val fade = 0.02f * dt
        gUp = max(gUp - fade, g?.lon ?: 0f)
        gDown = max(gDown - fade, g?.brake ?: 0f)
        gRight = max(gRight - fade, max(0f, g?.lat ?: 0f))
        gLeft = max(gLeft - fade, max(0f, -(g?.lat ?: 0f)))
        fill.color = Colors.CLAY
        val t = side * 0.035f
        fun peak(x: Float, y: Float) = c.drawRoundRect(RectF(x - t, y - t, x + t, y + t), t * 0.4f, t * 0.4f, fill)
        peak(cx, cy - gUp.coerceAtMost(1f) * half * 0.9f)
        peak(cx, cy + gDown.coerceAtMost(1f) * half * 0.9f)
        peak(cx + gRight.coerceAtMost(1f) * half * 0.9f, cy)
        peak(cx - gLeft.coerceAtMost(1f) * half * 0.9f, cy)

        if (!s.calibrated || s.calibrating) {
            label(side * 0.1f, PALE, Paint.Align.CENTER)
            c.drawText(if (s.calibrating) "HOLD STILL" else "CALIBRATE", cx, cy + side * 0.04f, text)
        } else {
            // The trail, fading towards the oldest point.
            if (s.trail.size > 1) {
                stroke.strokeWidth = side * 0.022f
                for (i in 1 until s.trail.size) {
                    val a = s.trail[i - 1]
                    val bb = s.trail[i]
                    stroke.color = Colors.ACCENT
                    stroke.alpha = (40 + 180 * i / s.trail.size)
                    c.drawLine(px(a), py(a), px(bb), py(bb), stroke)
                }
                stroke.alpha = 255
            }
            if (g != null) {
                val level = BrakeLevel.of(g.brake)
                fill.color = if (level == BrakeLevel.NONE) PALE else Colors.brake(level)
                val d = side * 0.05f
                c.drawRoundRect(RectF(px(g) - d, py(g) - d, px(g) + d, py(g) + d), d * 0.5f, d * 0.5f, fill)
            }
        }

        // The numbers, to the left of the plot.
        val tx = b.left
        label(h * 0.1f)
        c.drawText("G-FORCE", tx, b.top + h * 0.2f, text)
        number(h * 0.3f, if (g == null) Colors.DIM else PALE)
        c.drawText(g?.let { "%.2f".format(it.total) } ?: "–", tx, b.top + h * 0.5f, text)
        label(h * 0.085f)
        c.drawText("LAT", tx, b.top + h * 0.7f, text)
        c.drawText("LONG", tx, b.top + h * 0.9f, text)
        number(h * 0.11f, PALE, Paint.Align.RIGHT)
        val textRight = plot.left - side * 0.08f
        c.drawText(g?.let { "%.2f".format(abs(it.lat)) } ?: "–", textRight, b.top + h * 0.7f, text)
        c.drawText(g?.let { "%.2f".format(it.lon) } ?: "–", textRight, b.top + h * 0.9f, text)
    }

    // ---------------------------------------------------------------------------------------
    // The strip along the bottom
    // ---------------------------------------------------------------------------------------

    private fun strip(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        fill.shader = null
        fill.color = Colors.BOX
        c.drawRoundRect(b, h * 0.22f, h * 0.22f, fill)
        val lap = s.lap
        val run = s.run
        when {
            lap != null -> lapStrip(c, b, lap)
            run != null && (run.running || run.to60Ms != null || run.to100Ms != null || run.quarterMs != null) -> runStrip(c, b, run, units)
            else -> chips(c, b, s, units)
        }
    }

    private fun lapStrip(c: Canvas, b: RectF, lap: io.github.byjoelsamuel.drivesanity.track.LapView) {
        val h = b.height()
        val w = b.width()
        val base = b.top + h * 0.84f
        val pad = h * 0.3f
        label(h * 0.2f)
        c.drawText(if (lap.number == 0) "CROSS THE LINE" else "LAP ${lap.number}", b.left + pad, b.top + h * 0.3f, text)
        number(h * 0.5f, PALE)
        c.drawText(if (lap.number == 0) "ready" else lapText(lap.currentMs), b.left + pad, base, text)

        fun pair(x: Float, name: String, ms: Long?) {
            label(h * 0.2f)
            c.drawText(name, x, b.top + h * 0.3f, text)
            number(h * 0.4f, if (ms == null) Colors.DIM else PALE)
            c.drawText(lapText(ms), x, base, text)
        }
        pair(b.left + w * 0.36f, "LAST", lap.lastMs)
        pair(b.left + w * 0.52f, "BEST", lap.bestMs)

        // The delta bar: left of the middle is faster than the best lap, right is slower.
        val bx0 = b.left + w * 0.69f
        val bx1 = b.right - pad
        val mid = (bx0 + bx1) / 2
        val d = lap.deltaMs
        label(h * 0.2f, Colors.DIM, Paint.Align.LEFT)
        c.drawText("DELTA", bx0, b.top + h * 0.3f, text)
        number(h * 0.4f, when { d == null -> Colors.DIM; d <= 0 -> Colors.GOOD; else -> Colors.CLAY }, Paint.Align.RIGHT)
        c.drawText(deltaText(d), bx1, b.top + h * 0.5f, text)
        val track = RectF(bx0, b.top + h * 0.62f, bx1, b.top + h * 0.82f)
        fill.color = Colors.GRID
        c.drawRoundRect(track, track.height() / 2, track.height() / 2, fill)
        if (d != null) {
            val f = (d / 2000f).coerceIn(-1f, 1f) // the ends of the bar are two seconds
            fill.color = if (d <= 0) Colors.GOOD else Colors.CLAY
            val x = mid + f * (bx1 - bx0) / 2
            c.drawRoundRect(RectF(min(mid, x), track.top, max(mid, x), track.bottom), track.height() / 2, track.height() / 2, fill)
        }
        fill.color = PALE
        c.drawRect(mid - 1.5f, track.top - h * 0.04f, mid + 1.5f, track.bottom + h * 0.04f, fill)
    }

    private fun runStrip(c: Canvas, b: RectF, run: io.github.byjoelsamuel.drivesanity.track.RunView, units: Units) {
        val h = b.height()
        val w = b.width()
        val base = b.top + h * 0.84f
        val pad = h * 0.3f
        label(h * 0.2f)
        c.drawText(if (run.running) "TIMING…" else "LAST RUN", b.left + pad, b.top + h * 0.3f, text)
        number(h * 0.5f, if (run.running) Colors.SAND else PALE)
        c.drawText(if (run.running) secText(run.liveMs) else "done", b.left + pad, base, text)
        fun pair(x: Float, name: String, ms: Long?, extra: String? = null) {
            label(h * 0.2f)
            c.drawText(name, x, b.top + h * 0.3f, text)
            number(h * 0.4f, if (ms == null) Colors.DIM else PALE)
            c.drawText(if (ms == null) "–" else secText(ms) + (extra ?: ""), x, base, text)
        }
        pair(b.left + w * 0.32f, "0–60 MPH", run.to60Ms)
        pair(b.left + w * 0.5f, "0–100 KM/H", run.to100Ms)
        val trap = run.trapKmh?.let { " @ ${units.speed(it / 3.6f).roundToInt()} ${units.label}" } ?: ""
        pair(b.left + w * 0.7f, "¼ MILE", run.quarterMs, trap)
    }

    private fun secText(ms: Long?) = if (ms == null) "–" else "%.2f s".format(java.util.Locale.US, ms / 1000.0)

    private fun chips(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val items = listOf(
            "AIR / FUEL" to (if (s.petrol) s.lambda?.let { "%.1f".format(it * 14.7f) } else null),
            "LOAD" to s.loadPct?.let { "${it.roundToInt()}%" },
            "COOLANT" to s.coolantC?.let { if (units == Units.MPH) "${(it * 9 / 5 + 32).roundToInt()}°F" else "${it.roundToInt()}°C" },
            "TOP SPEED" to s.topSpeedMps.takeIf { it > 0.5f }?.let { "${units.speed(it).roundToInt()} ${units.label}" },
            "PEAK G" to s.peakG.takeIf { it > 0.05f }?.let { "%.2f".format(it) },
        )
        val cell = b.width() / items.size
        items.forEachIndexed { i, (name, value) ->
            val x = b.left + cell * i + h * 0.3f
            label(h * 0.2f)
            c.drawText(name, x, b.top + h * 0.32f, text)
            number(h * 0.44f, if (value == null) Colors.DIM else PALE)
            c.drawText(value ?: "–", x, b.top + h * 0.8f, text)
        }
    }

    private companion object {
        /** Pale, slightly blue off-white, for the numbers that matter. */
        val PALE = android.graphics.Color.rgb(222, 228, 236)
    }
}
