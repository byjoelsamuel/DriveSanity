package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.map.MapPainter
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.SpeedLimit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The two main car screens on a wide display.
 *
 * Gauges is a cluster in the style of a modern digital dash, with no round dials: stacks of
 * value-and-bar gauges on both sides and a huge gear number in the middle.
 * Map gives the left half to directions and a map that fades into the background, and puts the
 * instruments on the right.
 *
 * The speed counts up smoothly and rolls its digits, with a motion smear while it changes fast.
 */
class ClusterPainter(context: Context) {
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = numbers }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val map = MapPainter(labels, numbers)
    private val sport = SportPainter(context)
    private val speedRoll = RollingNumber()

    private fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = labels
        text.letterSpacing = 0.06f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    private fun number(size: Float, color: Int = Colors.FG, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = numbers
        text.letterSpacing = 0f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    private fun background(c: Canvas, r: RectF) {
        fill.color = Colors.BG
        c.drawRect(r, fill)
    }

    // ---------------------------------------------------------------------------------------
    // Gauges
    // ---------------------------------------------------------------------------------------

    fun drawGauges(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        background(c, r)
        val pad = r.height() * 0.035f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val h = inner.height()
        status(c, RectF(inner.left, inner.top, inner.right, inner.top + h * 0.07f), s)
        val body = RectF(inner.left, inner.top + h * 0.09f, inner.right, inner.bottom)
        val gap = body.width() * 0.035f
        val sideW = body.width() * 0.285f
        val left = RectF(body.left, body.top, body.left + sideW, body.bottom)
        val right = RectF(body.right - sideW, body.top, body.right, body.bottom)
        val mid = RectF(left.right + gap, body.top, right.left - gap, body.bottom)
        leftStack(c, left, s, units)
        centre(c, mid, s, units)
        rightStack(c, right, s, units)
    }

    private fun leftStack(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val rowH = b.height() / 4f
        fun row(i: Int) = RectF(b.left, b.top + i * rowH, b.right, b.top + (i + 1) * rowH - rowH * 0.1f)
        val rpm = s.rpm
        barRow(c, row(0), "RPM", rpm?.let { "%,d".format((it / 50f).roundToInt() * 50) } ?: if (s.obdLive) "–" else "no OBD2", rpm?.div(Revs.MAX), 16, ::rpmColor)
        val thr = s.throttlePct
        barRow(c, row(1), "THROTTLE", thr?.let { "${it.roundToInt()}%" } ?: "–", thr?.div(100f), 14, ::rampColor)
        val cool = s.coolantC
        val coolColor = when {
            cool == null -> Colors.DIM
            cool > 105f -> Colors.RED
            cool < 60f -> Colors.ACCENT // still cold: go easy
            else -> Colors.GOOD
        }
        barRow(c, row(2), "COOLANT", tempText(cool, units), cool?.let { (it - 40f) / 80f }, 14, { coolColor })
        val fuel = s.fuelPct
        val fuelColor = when {
            fuel == null -> Colors.DIM
            fuel < 12.5f -> Colors.RED
            fuel < 25f -> Colors.SAND
            else -> Colors.GOOD
        }
        if (fuel != null) {
            barRow(c, row(3), "FUEL", "${fuel.roundToInt()}%", fuel / 100f, 14, { fuelColor })
        } else {
            economyRow(c, row(3), s, units) // no fuel level from the car: show how much it's using instead
        }
    }

    private fun rightStack(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val scopeSize = min(b.width() * 0.5f, h * 0.36f)
        // The g-force readout beside a small scope.
        val g = s.g
        label(h * 0.055f)
        c.drawText("G-FORCE", b.left, b.top + h * 0.1f, text)
        number(h * 0.11f, if (g == null) Colors.DIM else Colors.FG)
        c.drawText(g?.let { "%.2f".format(it.total) } ?: "–", b.left, b.top + h * 0.24f, text)
        scope(c, RectF(b.right - scopeSize, b.top, b.right, b.top + scopeSize), s)

        val rowsTop = b.top + scopeSize + h * 0.03f
        val rowH = (b.bottom - rowsTop) / 3f
        fun row(i: Int) = RectF(b.left, rowsTop + i * rowH, b.right, rowsTop + (i + 1) * rowH - rowH * 0.1f)
        val v = s.volts
        val voltColor = when {
            v == null -> Colors.DIM
            v < 11.8f || v > 15.2f -> Colors.RED
            v < 12.2f -> Colors.SAND
            else -> Colors.GOOD
        }
        barRow(c, row(0), "BATTERY", v?.let { "%.1f V".format(it) } ?: "–", v?.let { (it - 11f) / 4.5f }, 14, { voltColor })
        barRow(c, row(1), "INTAKE", tempText(s.intakeC, units), s.intakeC?.let { (it + 10f) / 70f }, 14, ::rampColor)
        tires(c, row(2), s, units)
    }

    private fun centre(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val cx = b.centerX()
        // Gear: the biggest thing on the screen.
        number(
            h * (if (s.gear == null) 0.3f else 0.52f),
            when { s.gearless -> Colors.DIM; s.gear == null -> Colors.GRID; else -> Colors.FG },
            Paint.Align.CENTER,
        )
        c.drawText(s.gearText, cx, b.top + h * 0.43f, text)
        label(h * 0.06f, Colors.DIM, Paint.Align.CENTER)
        c.drawText(if (s.gearless) "NO FIXED GEARS" else "GEAR", cx, b.top + h * 0.5f, text)
        fill.color = Colors.GRID
        val lineH = max(2f, h * 0.006f)
        c.drawRect(cx - b.width() * 0.3f, b.top + h * 0.53f, cx + b.width() * 0.3f, b.top + h * 0.53f + lineH, fill)

        speedGroup(c, b, s, units, size = h * 0.29f, baseline = b.top + h * 0.815f, centred = true)

        // Braking, a slim bar along the bottom.
        val brake = s.g?.brake ?: 0f
        val level = BrakeLevel.of(brake)
        val color = Colors.brake(level)
        val y = b.top + h * 0.915f
        val inset = b.width() * 0.04f
        label(h * 0.05f)
        c.drawText("BRAKE", b.left + inset, y, text)
        number(h * 0.055f, color, Paint.Align.RIGHT)
        c.drawText(
            when {
                s.g == null -> "–"
                level == BrakeLevel.NONE -> "NONE"
                else -> "${level.label}  %.2f g".format(brake)
            },
            b.right - inset, y, text,
        )
        val bar = RectF(b.left + inset, y + h * 0.025f, b.right - inset, y + h * 0.025f + h * 0.04f)
        fill.color = Colors.GRID
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, fill)
        val frac = (brake / 0.8f).coerceIn(0f, 1f)
        if (frac > 0f) {
            fill.color = color
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * frac, bar.bottom), bar.height() / 2, bar.height() / 2, fill)
        }
    }

    /** The estimated fuel use, as a bar (more bar = thirstier). */
    private fun economyRow(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val frac = s.fuelLper100?.div(20f) ?: s.fuelLph?.div(6f)
        barRow(c, b, "ECONOMY", units.economyText(s.fuelLper100, s.fuelLph), frac, 14, ::rampColor)
    }

    // Sport lives in SportPainter.
    fun drawSport(c: Canvas, r: RectF, s: Snapshot, units: Units) = sport.draw(c, r, s, units)

    /** A caption and a big value on one line, and a row of bar segments under them. */
    private fun barRow(c: Canvas, b: RectF, name: String, value: String, frac: Float?, n: Int, colorOf: (Float) -> Int) {
        val h = b.height()
        label(h * 0.19f)
        c.drawText(name, b.left, b.top + h * 0.3f, text)
        number(h * 0.5f, if (frac == null) Colors.DIM else Colors.FG, Paint.Align.RIGHT)
        c.drawText(value, b.right, b.top + h * 0.56f, text)
        segments(c, RectF(b.left, b.top + h * 0.7f, b.right, b.top + h * 0.9f), frac, n, colorOf)
    }

    private fun segments(c: Canvas, b: RectF, frac: Float?, n: Int, colorOf: (Float) -> Int) {
        val seg = b.width() / n
        // The segment the level is partway through fades in, so the bar fills smoothly.
        val pos = if (frac == null) 0f else frac.coerceIn(0f, 1f) * n
        for (i in 0 until n) {
            val part = (pos - i).coerceIn(0f, 1f)
            val on = colorOf((i + 0.5f) / n)
            fill.color = if (part >= 0.98f) on else if (part > 0.02f) Colors.mix(Colors.GRID, on, part) else Colors.GRID
            val x = b.left + i * seg
            c.drawRect(x + seg * 0.1f, b.top, x + seg * 0.9f, b.bottom, fill)
        }
    }

    /** Sage while it's easy, then sand, clay and red as it climbs. */
    private fun rampColor(f: Float) = when {
        f < 0.62f -> Colors.GOOD
        f < 0.85f -> Colors.SAND
        f < 0.95f -> Colors.CLAY
        else -> Colors.RED
    }

    private fun rpmColor(f: Float): Int {
        // Sage up to about 70 % of the redline, then sand, clay and red.
        val rpm = f * Revs.MAX
        return when {
            rpm < Revs.REDLINE * 0.71f -> Colors.GOOD
            rpm < Revs.REDLINE * 0.95f -> Colors.SAND
            rpm < Revs.REDLINE -> Colors.CLAY
            else -> Colors.RED
        }
    }

    /** The g-meter as a small scope with a short trail. */
    private fun scope(c: Canvas, b: RectF, s: Snapshot) {
        val rad = min(b.width(), b.height()) / 2f * 0.8f
        val cx = b.centerX()
        val cy = b.centerY()
        stroke.strokeWidth = max(1.5f, rad * 0.02f)
        stroke.color = Colors.GRID
        c.drawCircle(cx, cy, rad * 0.5f, stroke)
        c.drawLine(cx - rad, cy, cx + rad, cy, stroke)
        c.drawLine(cx, cy - rad, cx, cy + rad, stroke)
        stroke.color = Colors.DIM
        c.drawCircle(cx, cy, rad, stroke)
        label(rad * 0.17f, Colors.DIM, Paint.Align.CENTER)
        c.drawText("ACCEL", cx, cy - rad * 1.07f, text)
        c.drawText("BRAKE", cx, cy + rad * 1.24f, text)
        if (!s.calibrated || s.calibrating) {
            label(rad * 0.2f, Colors.FG, Paint.Align.CENTER)
            c.drawText(if (s.calibrating) "HOLD STILL" else "CALIBRATE", cx, cy + rad * 0.07f, text)
            return
        }
        fun px(g: CarG): Pair<Float, Float> {
            val k = 1.2f / maxOf(g.total, 1.2f)
            return (cx + g.lat * k * rad) to (cy - g.lon * k * rad)
        }
        fill.color = Colors.ACCENT
        s.trail.forEachIndexed { i, g ->
            fill.alpha = 30 + 150 * i / maxOf(1, s.trail.size)
            val (x, y) = px(g)
            c.drawCircle(x, y, rad * 0.035f, fill)
        }
        fill.alpha = 255
        val g = s.g ?: return
        val level = BrakeLevel.of(g.brake)
        fill.color = if (level == BrakeLevel.NONE) Colors.ACCENT else Colors.brake(level)
        val (x, y) = px(g)
        c.drawCircle(x, y, rad * 0.1f, fill)
    }

    /** The four tire pressures in a row, coloured when one is low. Empty until the car can tell us. */
    private fun tires(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        label(h * 0.19f)
        c.drawText("TIRES ${Tpms.unit(units)}", b.left, b.top + h * 0.3f, text)
        val cellW = b.width() / 4f
        for (i in 0 until 4) {
            val psi = s.tires.getOrNull(i)
            val color = when {
                psi == null -> Colors.DIM
                Tpms.level(psi) == 2 -> Colors.RED
                Tpms.level(psi) == 1 -> Colors.SAND
                else -> Colors.FG
            }
            val x = b.left + i * cellW + cellW / 2
            number(h * 0.42f, color, Paint.Align.CENTER)
            c.drawText(Tpms.text(psi, units), x, b.top + h * 0.72f, text)
            label(h * 0.16f, Colors.DIM, Paint.Align.CENTER)
            c.drawText(Tpms.NAMES[i], x, b.top + h * 0.95f, text)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Map
    // ---------------------------------------------------------------------------------------

    fun drawMap(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        background(c, r)
        val pad = r.height() * 0.03f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val h = inner.height()
        val w = inner.width()
        val gap = w * 0.03f

        // Left: the map takes more than half the screen, with the revs under it and the next turn
        // laid over its top edge.
        val left = RectF(inner.left, inner.top, inner.left + w * 0.63f, inner.bottom)
        val rpmH = h * 0.2f
        val mapArea = RectF(left.left, left.top, left.right + gap * 0.4f, left.bottom - rpmH - h * 0.02f)
        map.draw(c, mapArea, s, units)
        if (Nav.route != null || Nav.message != null) {
            map.drawDirections(c, RectF(left.left, left.top, left.right, left.top + h * 0.32f), units, 238)
        }
        rpmBar(c, RectF(left.left, left.bottom - rpmH, left.right, left.bottom), s)

        // Right: speed and gear, how hard the pedal and the brake are pressed, then the temperatures.
        val b = RectF(left.right + gap, inner.top, inner.right, inner.bottom)
        var y = b.top
        fun row(f: Float) = RectF(b.left, y, b.right, y + h * f).also { y += h * f }
        status(c, row(0.07f), s)
        speedAndGear(c, row(0.34f), s, units)
        row(0.01f)
        val pedal = s.throttlePct
        levelBar(c, row(0.14f), "PEDAL", pedal?.let { "${it.roundToInt()}%" } ?: "–", pedal?.div(100f), rampColor((pedal ?: 0f) / 100f))
        row(0.01f)
        brakeBar(c, row(0.14f), s)
        row(0.03f)
        temperatures(c, row(0.26f), s, units)
    }

    private fun speedAndGear(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        // The gear gets a column of its own at the right; the speed, its units and the other units fit in what's left.
        val gearW = b.width() * 0.2f
        val speedW = b.width() - gearW - b.width() * 0.03f
        val size = min(h * 0.78f, speedW / 3.1f)
        speedGroup(c, RectF(b.left, b.top, b.left + speedW, b.bottom), s, units, size = size, baseline = b.top + h * 0.84f, centred = false)
        val gx = b.right - gearW
        label(h * 0.11f)
        c.drawText("GEAR", gx, b.top + h * 0.2f, text)
        number(h * (if (s.gear == null) 0.3f else 0.62f), when { s.gearless -> Colors.DIM; s.gear == null -> Colors.GRID; else -> Colors.FG })
        c.drawText(s.gearText, gx, b.top + h * 0.84f, text)
    }

    /**
     * The speed as rolling digits with the units beside them: [centred] in [b], or from its left
     * edge. The digits sit in a fixed three-digit slot so the units don't wander as it changes.
     */
    private fun speedGroup(c: Canvas, b: RectF, s: Snapshot, units: Units, size: Float, baseline: Float, centred: Boolean) {
        // Rounded first, so the digits only roll when the number changes, not sit half-turned at 69.3.
        val shown = speedRoll.update(s.speedMps?.let { units.speed(it).roundToInt().toFloat() })
        number(size)
        val cell = text.measureText("0")
        val slot = cell * 3
        val unitSize = size * 0.24f
        label(unitSize)
        val unitW = max(text.measureText(units.label), text.measureText("999 ${units.other.label}"))
        val gap = size * 0.1f
        val left = if (centred) b.centerX() - (slot + gap + unitW) / 2 else b.left
        if (shown == null) {
            number(size * 0.5f, Colors.DIM, Paint.Align.RIGHT)
            c.drawText("–", left + slot - cell * 0.3f, baseline, text)
        } else {
            val color = if (overLimit(s, units)) Colors.CLAY else Colors.FG
            number(size)
            val w = speedRoll.width(text, shown)
            speedRoll.draw(c, text, shown, speedRoll.rate, left + slot - w / 2, baseline, size, color)
        }
        val ux = left + slot + gap
        label(unitSize)
        c.drawText(units.label, ux, baseline - unitSize * 1.5f, text)
        label(unitSize * 0.85f)
        val other = units.other
        c.drawText("${s.speedMps?.let { other.speed(it).roundToInt() } ?: "–"} ${other.label}", ux, baseline, text)
    }

    private fun rpmBar(c: Canvas, b: RectF, s: Snapshot) {
        val h = b.height()
        val rpm = s.rpm
        label(h * 0.24f)
        c.drawText("RPM", b.left, b.top + h * 0.28f, text)
        number(h * 0.34f, if (rpm == null) Colors.DIM else Colors.rpm(rpm), Paint.Align.RIGHT)
        c.drawText(rpm?.let { "%,d".format((it / 50f).roundToInt() * 50) } ?: if (s.obdLive) "–" else "no OBD2", b.right, b.top + h * 0.33f, text)
        val n = (Revs.MAX / 250f).toInt()
        val bar = RectF(b.left, b.top + h * 0.45f, b.right, b.top + h * 0.78f)
        val seg = bar.width() / n
        for (i in 0 until n) {
            val from = i * 250f
            val off = if (from + 250f > Revs.REDLINE) Colors.RED_DIM else Colors.GRID
            val part = if (rpm == null) 0f else ((rpm - from) / 250f).coerceIn(0f, 1f)
            fill.color = if (part >= 0.98f) Colors.rpm(from + 125f) else if (part > 0.02f) Colors.mix(off, Colors.rpm(from + 125f), part) else off
            val x = bar.left + i * seg
            c.drawRect(x + seg * 0.12f, bar.top, x + seg * 0.88f, bar.bottom, fill)
        }
        label(h * 0.17f, Colors.DIM, Paint.Align.CENTER)
        for (k in 0..8) {
            val x = (bar.left + k * 1000f / Revs.MAX * bar.width()).coerceIn(bar.left + h * 0.08f, bar.right - h * 0.08f)
            text.color = if (k * 1000f >= Revs.REDLINE) Colors.RED else Colors.DIM
            c.drawText(k.toString(), x, b.top + h * 0.98f, text)
        }
    }

    /** A name, a value, and a rounded bar that fills left to right. */
    private fun levelBar(c: Canvas, b: RectF, name: String, value: String, frac: Float?, color: Int) {
        val h = b.height()
        label(h * 0.3f)
        c.drawText(name, b.left, b.top + h * 0.38f, text)
        number(h * 0.44f, if (frac == null) Colors.DIM else color, Paint.Align.RIGHT)
        c.drawText(value, b.right, b.top + h * 0.42f, text)
        val bar = RectF(b.left, b.top + h * 0.6f, b.right, b.top + h * 0.88f)
        fill.color = Colors.GRID
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, fill)
        val f = (frac ?: 0f).coerceIn(0f, 1f)
        if (f > 0f) {
            fill.color = color
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + max(bar.height(), bar.width() * f), bar.bottom), bar.height() / 2, bar.height() / 2, fill)
        }
    }

    private fun brakeBar(c: Canvas, b: RectF, s: Snapshot) {
        val h = b.height()
        val brake = s.g?.brake ?: 0f
        val level = BrakeLevel.of(brake)
        val color = Colors.brake(level)
        label(h * 0.3f)
        c.drawText("BRAKE", b.left, b.top + h * 0.38f, text)
        number(h * 0.44f, color, Paint.Align.RIGHT)
        c.drawText(
            when {
                s.g == null -> "–"
                level == BrakeLevel.NONE -> "NONE"
                else -> "${level.label}  %.2f g".format(brake)
            },
            b.right, b.top + h * 0.42f, text,
        )
        val bar = RectF(b.left, b.top + h * 0.6f, b.right, b.top + h * 0.88f)
        fill.color = Colors.GRID
        c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, fill)
        val frac = (brake / 0.8f).coerceIn(0f, 1f)
        if (frac > 0f) {
            fill.color = color
            c.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * frac, bar.bottom), bar.height() / 2, bar.height() / 2, fill)
        }
    }

    /** Coolant, battery and intake boxes. */
    private fun temperatures(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val gap = b.width() * 0.03f
        val bw = (b.width() - gap * 2) / 3f
        val cool = s.coolantC
        val v = s.volts
        box(c, RectF(b.left, b.top, b.left + bw, b.bottom), "COOLANT", tempText(cool, units),
            when { cool == null -> Colors.DIM; cool > 105f -> Colors.RED; cool < 60f -> Colors.ACCENT; else -> Colors.FG }, Colors.COOLANT)
        box(c, RectF(b.left + bw + gap, b.top, b.left + 2 * bw + gap, b.bottom), "BATTERY", v?.let { "%.1f V".format(it) } ?: "–",
            when { v == null -> Colors.DIM; v < 11.8f || v > 15.2f -> Colors.RED; v < 12.2f -> Colors.SAND; else -> Colors.FG }, Colors.BATTERY)
        box(c, RectF(b.left + 2 * (bw + gap), b.top, b.right, b.bottom), "INTAKE", tempText(s.intakeC, units), Colors.FG, Colors.INTAKE)
    }

    private fun box(c: Canvas, b: RectF, name: String, value: String, color: Int, tint: Int) {
        val h = b.height()
        fill.color = Colors.BOX
        c.drawRoundRect(b, h * 0.12f, h * 0.12f, fill)
        val inset = b.width() * 0.08f
        fill.color = tint
        c.drawRoundRect(RectF(b.left + inset, b.top + h * 0.19f, b.left + inset + h * 0.1f, b.top + h * 0.29f), h * 0.025f, h * 0.025f, fill)
        label(h * 0.16f)
        c.drawText(name, b.left + inset + h * 0.16f, b.top + h * 0.3f, text)
        number(h * 0.36f, if (value == "–") Colors.DIM else color)
        c.drawText(value, b.left + inset, b.bottom - h * 0.2f, text)
    }

    // ---------------------------------------------------------------------------------------

    private fun status(c: Canvas, b: RectF, s: Snapshot) {
        val size = b.height() * 0.78f
        label(size, if (s.accuracyM != null) Colors.DIM else Colors.REC)
        val gps = when {
            !s.gpsOn -> "NO GPS ACCESS"
            s.accuracyM == null -> "WAITING FOR GPS"
            else -> "GPS ±${s.accuracyM.roundToInt()} m"
        }
        val base = b.bottom - b.height() * 0.15f
        c.drawText(gps, b.left, base, text)
        val x = b.left + text.measureText("$gps    ")
        text.color = if (s.obdLive) Colors.GOOD else Colors.DIM
        c.drawText(if (s.obdLive) "OBD2 ●" else "NO OBD2", x, base, text)
        if (s.recording) {
            text.textAlign = Paint.Align.RIGHT
            text.color = Colors.REC
            c.drawText("● REC", b.right, base, text)
        }
    }

    private fun tempText(cel: Float?, units: Units): String =
        cel?.let { if (units == Units.MPH) "${(it * 9 / 5 + 32).roundToInt()}°F" else "${it.roundToInt()}°C" } ?: "–"

    /** More than 5 mph (8 km/h) over the posted limit, when one is known. */
    private fun overLimit(s: Snapshot, units: Units): Boolean {
        val mps = s.speedMps ?: return false
        val l = SpeedLimit.current ?: return false
        return if (units == Units.MPH) units.speed(mps) > l.inMph() + 5 else units.speed(mps) > l.inKmh() + 8
    }
}
