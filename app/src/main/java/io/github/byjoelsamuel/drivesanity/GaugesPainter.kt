package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.nav.SpeedLimit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The first car screen, "Gauges", built like a sports car's display and with no round dials:
 *
 *   - across the top, one long rev bar of leaning ticks that grow taller towards the redline, with a
 *     marker that holds the highest recent revs;
 *   - in the middle, the gear in a leaning frame, a very large speed, and the trip and fuel economy;
 *   - along the bottom, the throttle and the engine load as two wide bars, then the coolant, intake,
 *     battery, fuel and tire pressures in a row.
 *
 * Everything on it comes from the car (OBD2) and the GPS speed, none of it from the phone's motion sensor.
 *
 * Every size is a fraction of the space it's given, so it fills any shape of screen. The numbers that
 * are meant to be tweaked when the look is redesigned are together in [Look].
 */
class GaugesPainter(context: Context) {
    /** The knobs of the design: change these to restyle the screen without touching the drawing. */
    object Look {
        /** Share of the height taken by the revs, the middle row and the bottom row (the rest is gaps). */
        const val TOP = 0.31f
        const val MIDDLE = 0.43f
        const val BOTTOM = 0.20f

        /** How far the ticks lean, as a share of their height (0 is upright). */
        const val LEAN = 0.32f

        /** The italic slant of the big numbers (0 is upright). */
        const val ITALIC = 0.2f

        /** Ticks in the rev bar. */
        const val REV_TICKS = 60

        /** How tall the shortest rev tick is compared with the tallest. */
        const val REV_RAMP = 0.34f

        /** How long the peak-revs marker holds before it falls, in milliseconds. */
        const val PEAK_HOLD_MS = 2500L
    }

    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val path = Path()
    private val speedRoll = RollingNumber()
    private var peakRpm = 0f
    private var peakAtMs = 0L

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units, clockMs: Long = SystemClock.uptimeMillis()) {
        fill.color = Colors.BG
        c.drawRect(r, fill)
        val pad = r.height() * 0.04f
        val inner = RectF(r.left + pad, r.top + pad * 0.7f, r.right - pad, r.bottom - pad * 0.8f)
        val h = inner.height()
        val gap = h * (1f - Look.TOP - Look.MIDDLE - Look.BOTTOM) / 2f
        val top = RectF(inner.left, inner.top, inner.right, inner.top + h * Look.TOP)
        val middle = RectF(inner.left, top.bottom + gap, inner.right, top.bottom + gap + h * Look.MIDDLE)
        val bottom = RectF(inner.left, inner.bottom - h * Look.BOTTOM, inner.right, inner.bottom)

        revs(c, top, s, clockMs)
        val w = middle.width()
        val colGap = w * 0.03f
        val gearW = w * 0.24f
        val sideW = w * 0.24f
        gear(c, RectF(middle.left, middle.top, middle.left + gearW, middle.bottom), s)
        speed(c, RectF(middle.left + gearW + colGap, middle.top, middle.right - sideW - colGap, middle.bottom), s, units)
        tripAndEconomy(c, RectF(middle.right - sideW, middle.top, middle.right, middle.bottom), s, units)
        lower(c, bottom, s, units)
    }

    // ---- the rev bar -------------------------------------------------------------------------

    private fun revs(c: Canvas, b: RectF, s: Snapshot, clockMs: Long) {
        val h = b.height()
        val rpm = s.rpm
        // The line above the bar: the number on the left, how the car is talking to us on the right.
        val headH = h * 0.36f
        number(headH * 0.95f, if (rpm == null) Colors.DIM else Colors.rpm(rpm))
        val value = rpm?.let { "%,d".format((it / 10f).roundToInt() * 10) } ?: if (s.obdLive) "–" else "no OBD2"
        c.drawText(value, b.left, b.top + headH * 0.88f, text)
        val vw = text.measureText(value)
        label(headH * 0.4f)
        c.drawText("RPM", b.left + vw + headH * 0.25f, b.top + headH * 0.88f, text)
        status(c, RectF(b.right - b.width() * 0.45f, b.top, b.right, b.top + headH), s)

        // The bar itself: leaning ticks that grow taller to the right, lit up to the revs.
        val barTop = b.top + h * 0.43f
        val barBottom = b.top + h * 0.88f
        val barH = barBottom - barTop
        val n = Look.REV_TICKS
        val lean = barH * Look.LEAN
        val usable = b.width() - lean
        val pitch = usable / n
        val pos = if (rpm == null) 0f else (rpm / Revs.MAX).coerceIn(0f, 1f) * n
        val redFrom = Revs.REDLINE / Revs.MAX * n
        for (i in 0 until n) {
            val tall = barH * (Look.REV_RAMP + (1f - Look.REV_RAMP) * i / (n - 1))
            val lit = (pos - i).coerceIn(0f, 1f)
            val atRpm = (i + 0.5f) / n * Revs.MAX
            val inRed = i + 0.5f >= redFrom
            val on = Colors.rpm(atRpm)
            fill.color = when {
                lit >= 0.98f -> on
                lit > 0.02f -> Colors.mix(if (inRed) Colors.RED_DIM else Colors.GRID, on, lit)
                inRed -> Colors.RED_DIM
                else -> Colors.GRID
            }
            tick(c, b.left + i * pitch, barBottom, pitch * 0.64f, tall, lean * tall / barH)
        }

        // The highest recent revs, held for a moment, as a small marker above the bar.
        if (rpm != null) {
            if (rpm >= peakRpm || clockMs - peakAtMs > Look.PEAK_HOLD_MS) {
                peakRpm = rpm
                peakAtMs = clockMs
            }
            val f = (peakRpm / Revs.MAX).coerceIn(0f, 1f)
            val x = b.left + f * usable + lean * (Look.REV_RAMP + (1f - Look.REV_RAMP) * f) * 0.5f
            val tallHere = barH * (Look.REV_RAMP + (1f - Look.REV_RAMP) * f)
            val tipY = barBottom - tallHere - barH * 0.06f
            fill.color = Colors.ACCENT
            path.reset()
            path.moveTo(x - barH * 0.12f, tipY - barH * 0.2f)
            path.lineTo(x + barH * 0.12f, tipY - barH * 0.2f)
            path.lineTo(x, tipY)
            path.close()
            c.drawPath(path, fill)
        } else {
            peakRpm = 0f
        }

        // Thousands under the bar, and the redline as a thin line.
        label(h * 0.1f)
        var k = 0
        while (k * 1000f <= Revs.MAX) {
            val x = b.left + (k * 1000f / Revs.MAX) * usable
            text.color = if (k * 1000f >= Revs.REDLINE) Colors.RED else Colors.DIM
            c.drawText(k.toString(), x, b.bottom, text)
            k++
        }
    }

    /** One leaning bar standing on [baseY], its bottom-left corner at [x]. */
    private fun tick(c: Canvas, x: Float, baseY: Float, width: Float, height: Float, lean: Float) {
        path.reset()
        path.moveTo(x, baseY)
        path.lineTo(x + width, baseY)
        path.lineTo(x + width + lean, baseY - height)
        path.lineTo(x + lean, baseY - height)
        path.close()
        c.drawPath(path, fill)
    }

    // ---- gear --------------------------------------------------------------------------------

    private fun gear(c: Canvas, b: RectF, s: Snapshot) {
        val h = b.height()
        label(h * 0.09f)
        c.drawText(if (s.gearless) "NO FIXED GEARS" else "GEAR", b.left, b.top + h * 0.09f, text)
        // A leaning frame, the way a race car's shift display is boxed.
        val frame = RectF(b.left, b.top + h * 0.16f, b.right, b.bottom)
        val lean = frame.height() * 0.16f
        path.reset()
        path.moveTo(frame.left + lean, frame.top)
        path.lineTo(frame.right, frame.top)
        path.lineTo(frame.right - lean, frame.bottom)
        path.lineTo(frame.left, frame.bottom)
        path.close()
        if (s.gear != null) {
            fill.color = Colors.BOX
            c.drawPath(path, fill)
        }
        stroke.color = if (s.gear == null) Colors.GRID else Colors.DIM
        stroke.strokeWidth = max(2f, h * 0.012f)
        c.drawPath(path, stroke)
        val size = frame.height() * (if (s.gear == null) 0.5f else 0.8f)
        number(size, when { s.gearless -> Colors.DIM; s.gear == null -> Colors.GRID; else -> Colors.FG }, Paint.Align.CENTER)
        italic(c, s.gearText, frame.centerX(), frame.bottom - frame.height() * 0.2f)
    }

    // ---- speed -------------------------------------------------------------------------------

    private fun speed(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val size = min(h * 0.92f, b.width() / 2.3f)
        val baseline = b.top + h * 0.78f
        val shown = speedRoll.update(s.speedMps?.let { units.speed(it).roundToInt().toFloat() })
        number(size)
        val cell = text.measureText("0")
        val slot = cell * 3
        val unitSize = size * 0.2f
        label(unitSize)
        val unitW = max(text.measureText(units.label), text.measureText("999 ${units.other.label}"))
        val gap = size * 0.08f
        val left = b.centerX() - (slot + gap + unitW) / 2
        if (shown == null) {
            number(size * 0.5f, Colors.DIM, Paint.Align.RIGHT)
            italic(c, "–", left + slot - cell * 0.3f, baseline)
        } else {
            val color = if (overLimit(s, units)) Colors.CLAY else Colors.FG
            number(size)
            val w = speedRoll.width(text, shown)
            c.save()
            c.translate(left + slot - w / 2, baseline)
            c.skew(-Look.ITALIC, 0f)
            speedRoll.draw(c, text, shown, speedRoll.rate, 0f, 0f, size, color)
            c.restore()
        }
        val ux = left + slot + gap
        label(unitSize)
        c.drawText(units.label, ux, baseline - unitSize * 1.6f, text)
        label(unitSize * 0.85f)
        val other = units.other
        c.drawText("${s.speedMps?.let { other.speed(it).roundToInt() } ?: "–"} ${other.label}", ux, baseline, text)
        // The posted limit, when it's known.
        SpeedLimit.current?.let { lim ->
            val value = if (units == Units.MPH) lim.inMph() else lim.inKmh()
            label(h * 0.075f, if (overLimit(s, units)) Colors.CLAY else Colors.DIM, Paint.Align.CENTER)
            c.drawText("LIMIT $value", b.centerX(), b.bottom - h * 0.02f, text)
        }
    }

    private fun overLimit(s: Snapshot, units: Units): Boolean {
        val mps = s.speedMps ?: return false
        val l = SpeedLimit.current ?: return false
        return if (units == Units.MPH) units.speed(mps) > l.inMph() + 5 else units.speed(mps) > l.inKmh() + 8
    }

    // ---- trip and economy --------------------------------------------------------------------

    /** The distance this trip, and how thirsty the car is right now (an estimate from the air flow). */
    private fun tripAndEconomy(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val half = h / 2f
        stat(c, RectF(b.left, b.top, b.right, b.top + half), "TRIP", "%.1f".format(units.distance(s.tripM)), units.distanceLabel, true)
        val econ = units.economyText(s.fuelLper100, s.fuelLph)
        stat(c, RectF(b.left, b.top + half, b.right, b.bottom), "ECONOMY", econ, "", econ != "–")
    }

    /** A caption over a big value, with its unit small beside it. */
    private fun stat(c: Canvas, b: RectF, name: String, value: String, unit: String, known: Boolean) {
        val h = b.height()
        label(h * 0.15f)
        c.drawText(name, b.left, b.top + h * 0.2f, text)
        number(h * 0.42f, if (known) Colors.FG else Colors.DIM)
        c.drawText(value, b.left, b.top + h * 0.72f, text)
        if (unit.isNotEmpty()) {
            val w = text.measureText(value)
            label(h * 0.17f)
            c.drawText(unit, b.left + w + h * 0.08f, b.top + h * 0.72f, text)
        }
    }

    // ---- the bottom row ----------------------------------------------------------------------

    private fun lower(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val w = b.width()
        val pedalsW = w * 0.34f
        val gap = w * 0.025f
        pedals(c, RectF(b.left, b.top, b.left + pedalsW, b.bottom), s)

        val cells = RectF(b.left + pedalsW + gap, b.top, b.right, b.bottom)
        val n = 5
        val cellGap = cells.width() * 0.02f
        val cw = (cells.width() - cellGap * (n - 1)) / n
        fun cell(i: Int) = RectF(cells.left + i * (cw + cellGap), cells.top, cells.left + i * (cw + cellGap) + cw, cells.bottom)

        val cool = s.coolantC
        readout(
            c, cell(0), "COOLANT", tempText(cool, units), cool?.let { (it - 40f) / 80f },
            when { cool == null -> Colors.DIM; cool > 105f -> Colors.RED; cool < 60f -> Colors.ACCENT; else -> Colors.GOOD },
        )
        readout(c, cell(1), "INTAKE", tempText(s.intakeC, units), s.intakeC?.let { (it + 10f) / 70f }, Colors.COOLANT)
        val v = s.volts
        readout(
            c, cell(2), "BATTERY", v?.let { "%.1f V".format(it) } ?: "–", v?.let { (it - 11f) / 4.5f },
            when { v == null -> Colors.DIM; v < 11.8f || v > 15.2f -> Colors.RED; v < 12.2f -> Colors.SAND; else -> Colors.GOOD },
        )
        val fuel = s.fuelPct
        readout(
            c, cell(3), "FUEL", fuel?.let { "${it.roundToInt()}%" } ?: "–", fuel?.div(100f),
            when { fuel == null -> Colors.DIM; fuel < 12.5f -> Colors.RED; fuel < 25f -> Colors.SAND; else -> Colors.GOOD },
        )
        tires(c, cell(4), s, units)
    }

    /** Throttle over engine load: two wide bars, both read from the car. */
    private fun pedals(c: Canvas, b: RectF, s: Snapshot) {
        val h = b.height()
        val rowH = h / 2f
        val ramp = { f: Float -> when { f < 0.62f -> Colors.GOOD; f < 0.85f -> Colors.SAND; f < 0.95f -> Colors.CLAY; else -> Colors.RED } }
        val thr = s.throttlePct
        pedalRow(c, RectF(b.left, b.top, b.right, b.top + rowH * 0.94f), "THROTTLE", thr?.let { "${it.roundToInt()}%" } ?: "–", thr?.div(100f), ramp)
        val load = s.loadPct
        pedalRow(c, RectF(b.left, b.top + rowH, b.right, b.bottom), "ENGINE LOAD", load?.let { "${it.roundToInt()}%" } ?: "–", load?.div(100f), ramp)
    }

    private fun pedalRow(c: Canvas, b: RectF, name: String, value: String, frac: Float?, colorOf: (Float) -> Int) {
        val h = b.height()
        label(h * 0.33f)
        c.drawText(name, b.left, b.top + h * 0.34f, text)
        number(h * 0.42f, if (frac == null) Colors.DIM else Colors.FG, Paint.Align.RIGHT)
        c.drawText(value, b.right, b.top + h * 0.4f, text)
        val n = 36
        val barTop = b.top + h * 0.5f
        val barH = b.bottom - barTop
        val lean = barH * Look.LEAN
        val pitch = (b.width() - lean) / n
        val pos = if (frac == null) 0f else frac.coerceIn(0f, 1f) * n
        for (i in 0 until n) {
            val part = (pos - i).coerceIn(0f, 1f)
            val on = colorOf((i + 0.5f) / n)
            fill.color = if (part >= 0.98f) on else if (part > 0.02f) Colors.mix(Colors.GRID, on, part) else Colors.GRID
            tick(c, b.left + i * pitch, b.bottom, pitch * 0.66f, barH, lean)
        }
    }

    /** A caption, a value and a short bar of ticks under it. */
    private fun readout(c: Canvas, b: RectF, name: String, value: String, frac: Float?, color: Int) {
        val h = b.height()
        label(h * 0.2f)
        c.drawText(name, b.left, b.top + h * 0.2f, text)
        number(h * 0.42f, if (frac == null) Colors.DIM else Colors.FG)
        c.drawText(value, b.left, b.top + h * 0.68f, text)
        val n = 14
        val barTop = b.top + h * 0.8f
        val pitch = b.width() / n
        val pos = if (frac == null) 0f else frac.coerceIn(0f, 1f) * n
        for (i in 0 until n) {
            val part = (pos - i).coerceIn(0f, 1f)
            fill.color = if (part >= 0.98f) color else if (part > 0.02f) Colors.mix(Colors.GRID, color, part) else Colors.GRID
            c.drawRect(b.left + i * pitch + pitch * 0.15f, barTop, b.left + i * pitch + pitch * 0.85f, b.bottom, fill)
        }
    }

    /** The four tire pressures, two by two, coloured when one is low. Dashes until the car can tell us. */
    private fun tires(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        label(h * 0.2f)
        c.drawText("TIRES ${Tpms.unit(units)}", b.left, b.top + h * 0.2f, text)
        val cw = b.width() / 2f
        for (i in 0 until 4) {
            val psi = s.tires.getOrNull(i)
            val color = when {
                psi == null -> Colors.DIM
                Tpms.level(psi) == 2 -> Colors.RED
                Tpms.level(psi) == 1 -> Colors.SAND
                else -> Colors.FG
            }
            val x = b.left + (i % 2) * cw
            val y = b.top + h * (if (i < 2) 0.56f else 0.94f)
            number(h * 0.3f, color)
            c.drawText(Tpms.text(psi, units), x + cw * 0.38f, y, text)
            label(h * 0.16f, Colors.DIM)
            c.drawText(Tpms.NAMES[i], x, y, text)
        }
    }

    // ---- shared ------------------------------------------------------------------------------

    private fun status(c: Canvas, b: RectF, s: Snapshot) {
        val size = b.height() * 0.34f
        label(size, if (s.accuracyM != null || s.demo) Colors.DIM else Colors.REC, Paint.Align.RIGHT)
        val gps = when {
            s.demo -> "DEMO DRIVE"
            !s.gpsOn -> "NO GPS ACCESS"
            s.accuracyM == null -> "WAITING FOR GPS"
            else -> "GPS ±${s.accuracyM.roundToInt()} m"
        }
        val base = b.top + b.height() * 0.42f
        val obd = if (s.obdLive) "OBD2 ●" else "NO OBD2"
        // Right to left: recording, OBD2, GPS.
        var x = b.right
        if (s.recording) {
            text.color = Colors.REC
            c.drawText("● REC", x, base, text)
            x -= text.measureText("● REC    ")
        }
        text.color = if (s.obdLive) Colors.GOOD else Colors.DIM
        c.drawText(obd, x, base, text)
        x -= text.measureText("$obd    ")
        text.color = if (s.accuracyM != null || s.demo) Colors.DIM else Colors.REC
        c.drawText(gps, x, base, text)
    }

    /** Draws [value] leaning forward, standing on [baseline] and centred on [x], in the current paint. */
    private fun italic(c: Canvas, value: String, x: Float, baseline: Float) {
        c.save()
        c.translate(x, baseline)
        c.skew(-Look.ITALIC, 0f)
        c.drawText(value, 0f, 0f, text)
        c.restore()
    }

    private fun tempText(cel: Float?, units: Units): String =
        cel?.let { if (units == Units.MPH) "${(it * 9 / 5 + 32).roundToInt()}°F" else "${it.roundToInt()}°C" } ?: "–"

    private fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = labels; text.letterSpacing = 0.06f; text.textSize = size; text.color = color; text.textAlign = align
    }

    private fun number(size: Float, color: Int = Colors.FG, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = numbers; text.letterSpacing = 0f; text.textSize = size; text.color = color; text.textAlign = align
    }
}
