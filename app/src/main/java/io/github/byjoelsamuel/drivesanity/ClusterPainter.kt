package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
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
    private val numbers = Fonts.semibold(context)
    private val labels = Fonts.medium(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = numbers }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val map = MapPainter(labels, numbers)
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

    // ---------------------------------------------------------------------------------------
    // Sport: laid out like a supercar's digital cluster. Economy with a small arc, then the air/fuel
    // ratio and coolant on the left; a huge gear in the middle with a thin rev line under it; throttle,
    // brake and g-force on the right; and the tires as a column of rings.
    // ---------------------------------------------------------------------------------------

    private val pale = Color.rgb(205, 213, 224)
    private val teal = Color.rgb(112, 184, 168)
    private val path = Path()

    fun drawSport(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        background(c, r)
        val pad = r.height() * 0.035f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val h = inner.height()
        val top = RectF(inner.left, inner.top, inner.right, inner.top + h * 0.07f)
        status(c, top, s)
        // Battery and intake air don't get a row here; they sit in the middle of the top line.
        label(top.height() * 0.78f, Colors.DIM, Paint.Align.CENTER)
        val volts = s.volts?.let { "%.1f V".format(it) } ?: "–"
        c.drawText("BATTERY $volts     INTAKE ${tempText(s.intakeC, units)}", top.centerX(), top.bottom - top.height() * 0.15f, text)
        val body = RectF(inner.left, inner.top + h * 0.1f, inner.right, inner.bottom)
        val w = body.width()
        fun col(x0: Float, x1: Float) = RectF(body.left + w * x0, body.top, body.left + w * x1, body.bottom)
        sportLeft(c, col(0f, 0.325f), s, units)
        sportCentre(c, col(0.36f, 0.64f), s, units)
        sportRight(c, col(0.675f, 0.9f), s, units)
        tireRings(c, col(0.925f, 1f), s, units)
    }

    private fun sportLeft(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val rowH = b.height() / 3f
        fun row(i: Int) = RectF(b.left, b.top + i * rowH, b.right, b.top + (i + 1) * rowH)
        // Economy: a small arc and a big teal number.
        val e = row(0)
        val frac = s.fuelLper100?.div(20f) ?: s.fuelLph?.div(6f)
        val shown = units.economyText(s.fuelLper100, s.fuelLph)
        val space = shown.indexOf(' ')
        val num = if (space > 0) shown.substring(0, space) else shown
        val unit = if (space > 0) shown.substring(space + 1) else ""
        arc(c, RectF(e.left, e.top + rowH * 0.1f, e.left + rowH * 0.82f, e.top + rowH * 0.1f + rowH * 0.82f), frac)
        val tx = e.left + rowH * 1.0f
        label(rowH * 0.15f)
        c.drawText("ECONOMY", tx, e.top + rowH * 0.27f, text)
        number(rowH * 0.5f, if (frac == null) Colors.DIM else teal, Paint.Align.RIGHT)
        c.drawText(num, e.right, e.top + rowH * 0.72f, text)
        label(rowH * 0.15f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText(unit.uppercase(), e.right, e.top + rowH * 0.92f, text)

        val afr = if (s.petrol) s.lambda?.let { it * 14.7f } else null
        statRow(c, row(1), "AIR / FUEL", afr?.let { "%.1f".format(it) } ?: "–", pale, afr?.let { (it - 10f) / 8f }, warm = true)
        val cool = s.coolantC
        val coolColor = if (cool != null && cool > 105f) Colors.RED else pale
        statRow(c, row(2), "COOLANT", tempText(cool, units), coolColor, cool?.let { (it - 40f) / 80f }, warm = true)
    }

    private fun sportRight(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val rowH = b.height() / 3f
        fun row(i: Int) = RectF(b.left, b.top + i * rowH, b.right, b.top + (i + 1) * rowH)
        val thr = s.throttlePct
        statRow(c, row(0), "THROTTLE", thr?.let { "${it.roundToInt()}%" } ?: "–", pale, thr?.div(100f), warm = false)
        val g = s.g
        val brake = g?.brake ?: 0f
        val brakeColor = if (g == null) pale else Colors.brake(BrakeLevel.of(brake)).let { if (BrakeLevel.of(brake) == BrakeLevel.NONE) pale else it }
        statRow(c, row(1), "BRAKE", if (g == null) "–" else "%.2f g".format(brake), brakeColor, if (g == null) null else (brake / 0.8f).coerceIn(0f, 1f), warm = false)
        statRow(c, row(2), "G-FORCE", g?.let { "%.2f".format(it.total) } ?: "–", pale, g?.let { (it.total / 1.2f).coerceIn(0f, 1f) }, warm = false)
    }

    /** The gear, huge, with "GEAR" under it, a thin line that is the rev counter, and the speed. */
    private fun sportCentre(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val cx = b.centerX()
        val level = Alerts.shiftLevel(s.rpm, Revs.REDLINE)
        val flashOn = (Alerts.now() / 110) % 2 == 0L
        val gearColor = when {
            s.gearless -> Colors.DIM
            s.gear == null -> Colors.GRID
            level == 2 -> if (flashOn) Colors.RED else Colors.GRID
            level == 1 -> Colors.SAND
            else -> pale
        }
        number(h * (if (s.gear == null) 0.3f else 0.66f), gearColor, Paint.Align.CENTER)
        c.drawText(s.gearText, cx, b.top + h * 0.5f, text)
        label(h * 0.07f, pale, Paint.Align.CENTER)
        text.typeface = numbers
        c.drawText(if (s.gearless) "NO FIXED GEARS" else "GEAR", cx, b.top + h * 0.6f, text)

        // The rev line: fills left to right, sage, then sand, then red at the redline.
        val line = RectF(b.left + b.width() * 0.08f, b.top + h * 0.655f, b.right - b.width() * 0.08f, b.top + h * 0.655f + max(4f, h * 0.014f))
        fill.color = Colors.GRID
        c.drawRoundRect(line, line.height() / 2, line.height() / 2, fill)
        val rpm = s.rpm
        if (rpm != null) {
            val f = (rpm / Revs.MAX).coerceIn(0f, 1f)
            fill.color = Colors.rpm(rpm).let { if (level == 0) pale else it }
            c.drawRoundRect(RectF(line.left, line.top, line.left + line.width() * f, line.bottom), line.height() / 2, line.height() / 2, fill)
            // Where the redline is, so you can see how far off it is.
            fill.color = Colors.RED
            val rx = line.left + line.width() * (Revs.REDLINE / Revs.MAX)
            c.drawRect(rx - 2f, line.top - line.height() * 0.8f, rx + 2f, line.bottom + line.height() * 0.8f, fill)
        }

        speedGroup(c, b, s, units, size = h * 0.24f, baseline = b.top + h * 0.9f, centred = true)
        label(h * 0.05f, Colors.DIM, Paint.Align.CENTER)
        c.drawText(rpm?.let { "%,d rpm".format((it / 50f).roundToInt() * 50) } ?: "no OBD2", cx, b.top + h * 0.985f, text)
    }

    /**
     * A small caption, a value in big pale numbers on the right, and a bar under them that ends in an
     * arrowhead with a short red tick at the far end of the track. [warm] colours the bar from red
     * through sand to sage; otherwise it runs from pale to teal.
     */
    private fun statRow(c: Canvas, b: RectF, name: String, value: String, valueColor: Int, frac: Float?, warm: Boolean) {
        val h = b.height()
        label(h * 0.15f)
        c.drawText(name, b.left, b.top + h * 0.3f, text)
        number(h * 0.42f, if (frac == null) Colors.DIM else valueColor, Paint.Align.RIGHT)
        c.drawText(value, b.right, b.top + h * 0.6f, text)

        val thick = h * 0.085f
        val y = b.top + h * 0.8f
        val track = RectF(b.left, y - thick / 2, b.right - b.width() * 0.07f, y + thick / 2)
        fill.shader = null
        fill.color = Colors.GRID
        c.drawRoundRect(track, thick / 2, thick / 2, fill)
        // The red tick at the end of the track.
        fill.color = Colors.RED
        val tx = b.right - b.width() * 0.02f
        c.drawRoundRect(RectF(tx - thick * 0.25f, y - thick * 0.95f, tx + thick * 0.25f, y + thick * 0.95f), thick * 0.25f, thick * 0.25f, fill)
        if (frac == null) return
        val len = track.width() * frac.coerceIn(0f, 1f)
        if (len < thick) return
        val head = thick * 1.9f
        val bodyEnd = track.left + len - head * 0.4f
        val colors = if (warm) intArrayOf(Colors.RED, Colors.SAND, Colors.GOOD, teal) else intArrayOf(pale, pale, teal)
        fill.shader = LinearGradient(track.left, 0f, track.left + len, 0f, colors, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(track.left, track.top, bodyEnd, track.bottom), thick / 2, thick / 2, fill)
        path.reset()
        path.moveTo(track.left + len, y)
        path.lineTo(bodyEnd - head * 0.2f, y - head * 0.5f)
        path.lineTo(bodyEnd - head * 0.2f, y + head * 0.5f)
        path.close()
        fill.color = teal
        fill.shader = null
        c.drawPath(path, fill)
    }

    /** A quarter arc, from the left round to the top, that fills as [frac] goes up. */
    private fun arc(c: Canvas, box: RectF, frac: Float?) {
        val thick = box.height() * 0.13f
        stroke.strokeWidth = thick
        stroke.strokeCap = Paint.Cap.ROUND
        val oval = RectF(box.left + thick / 2, box.top + thick / 2, box.right * 2 - box.left - thick / 2, box.bottom * 2 - box.top - thick / 2)
        stroke.shader = null
        stroke.color = Colors.GRID
        c.drawArc(oval, 180f, 90f, false, stroke)
        if (frac != null && frac > 0.02f) {
            val sweep = 90f * frac.coerceIn(0f, 1f)
            val cx = oval.centerX()
            val cy = oval.centerY()
            stroke.shader = SweepGradient(cx, cy, intArrayOf(pale, pale, teal, teal), floatArrayOf(0f, 0.5f, 0.75f, 1f))
            c.drawArc(oval, 180f, sweep, false, stroke)
            stroke.shader = null
            // The arrowhead at the end of the arc.
            val rad = oval.width() / 2
            val a = Math.toRadians((180f + sweep).toDouble())
            val px = cx + rad * kotlin.math.cos(a).toFloat()
            val py = cy + rad * kotlin.math.sin(a).toFloat()
            val tx = -kotlin.math.sin(a).toFloat()
            val ty = kotlin.math.cos(a).toFloat()
            val nx = kotlin.math.cos(a).toFloat()
            val ny = kotlin.math.sin(a).toFloat()
            val hs = thick * 1.3f
            path.reset()
            path.moveTo(px + tx * hs * 1.3f, py + ty * hs * 1.3f)
            path.lineTo(px + nx * hs * 0.9f, py + ny * hs * 0.9f)
            path.lineTo(px - nx * hs * 0.9f, py - ny * hs * 0.9f)
            path.close()
            fill.color = teal
            c.drawPath(path, fill)
        }
        stroke.strokeCap = Paint.Cap.BUTT
    }

    /** The four tire pressures as a column of rings, like the warning lamps down the side of the cluster. */
    private fun tireRings(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val cell = b.height() / 4f
        val d = min(b.width(), cell * 0.92f)
        for (i in 0 until 4) {
            val psi = s.tires.getOrNull(i)
            val color = when {
                psi == null -> Colors.DIM
                Tpms.level(psi) == 2 -> Colors.RED
                Tpms.level(psi) == 1 -> Colors.SAND
                else -> teal
            }
            val cx = b.centerX()
            val cy = b.top + cell * (i + 0.5f)
            stroke.strokeWidth = max(2f, d * 0.045f)
            stroke.color = color
            c.drawCircle(cx, cy, d / 2 - stroke.strokeWidth, stroke)
            number(d * 0.34f, if (psi == null) Colors.DIM else pale, Paint.Align.CENTER)
            c.drawText(Tpms.text(psi, units), cx, cy + d * 0.07f, text)
            label(d * 0.17f, Colors.DIM, Paint.Align.CENTER)
            c.drawText(Tpms.NAMES[i], cx, cy + d * 0.3f, text)
        }
    }

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
        val lit = if (frac == null) 0 else (frac.coerceIn(0f, 1f) * n).roundToInt()
        for (i in 0 until n) {
            fill.color = if (i < lit) colorOf((i + 0.5f) / n) else Colors.GRID
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
        val pad = r.height() * 0.035f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val h = inner.height()
        val gap = inner.width() * 0.03f
        val left = RectF(inner.left, inner.top, inner.left + inner.width() * 0.48f, inner.bottom)

        // The next turn in its own box above the map, when there is a route.
        var mapTop = left.top
        if (Nav.route != null || Nav.message != null) {
            val boxH = h * 0.26f
            map.drawDirections(c, RectF(left.left, left.top, left.right, left.top + boxH), units)
            mapTop = left.top + boxH + h * 0.02f
        }
        // The map fades out at its edges, so it can run a little into the gap.
        map.draw(c, RectF(left.left, mapTop, left.right + gap * 0.4f, left.bottom), s, units)

        val b = RectF(left.right + gap, inner.top, inner.right, inner.bottom)
        var y = b.top
        fun row(f: Float) = RectF(b.left, y, b.right, y + h * f).also { y += h * f }
        status(c, row(0.07f), s)
        speedAndGear(c, row(0.355f), s, units)
        rpmBar(c, row(0.17f), s)
        row(0.015f)
        brakeBar(c, row(0.12f), s)
        row(0.02f)
        bottomRow(c, row(0.25f), s, units)
    }

    private fun speedAndGear(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val h = b.height()
        val speedW = b.width() * 0.66f
        speedGroup(c, RectF(b.left, b.top, b.left + speedW, b.bottom), s, units, size = h * 0.78f, baseline = b.top + h * 0.84f, centred = false)
        val gx = b.left + speedW + b.width() * 0.04f
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
            fill.color = when {
                rpm != null && rpm > from -> Colors.rpm(from + 125f)
                from + 250f > Revs.REDLINE -> Colors.RED_DIM
                else -> Colors.GRID
            }
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

    private fun brakeBar(c: Canvas, b: RectF, s: Snapshot) {
        val h = b.height()
        val brake = s.g?.brake ?: 0f
        val level = BrakeLevel.of(brake)
        val color = Colors.brake(level)
        label(h * 0.3f)
        c.drawText("BRAKE", b.left, b.top + h * 0.38f, text)
        number(h * 0.38f, color, Paint.Align.RIGHT)
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

    /** Coolant, battery and intake boxes, with fuel and the tires beside them. */
    private fun bottomRow(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val boxesW = b.width() * 0.54f
        val gap = b.width() * 0.02f
        val bw = (boxesW - gap * 2) / 3f
        val cool = s.coolantC
        val v = s.volts
        box(c, RectF(b.left, b.top, b.left + bw, b.bottom), "COOLANT", tempText(cool, units),
            when { cool == null -> Colors.DIM; cool > 105f -> Colors.RED; cool < 60f -> Colors.ACCENT; else -> Colors.FG }, Colors.COOLANT)
        box(c, RectF(b.left + bw + gap, b.top, b.left + 2 * bw + gap, b.bottom), "BATTERY", v?.let { "%.1f V".format(it) } ?: "–",
            when { v == null -> Colors.DIM; v < 11.8f || v > 15.2f -> Colors.RED; v < 12.2f -> Colors.SAND; else -> Colors.FG }, Colors.BATTERY)
        box(c, RectF(b.left + 2 * (bw + gap), b.top, b.left + boxesW, b.bottom), "INTAKE", tempText(s.intakeC, units), Colors.FG, Colors.INTAKE)

        val rb = RectF(b.left + boxesW + gap * 1.5f, b.top, b.right, b.bottom)
        val fuel = s.fuelPct
        val fh = rb.height() * 0.34f
        // Fuel level when the car reports it; otherwise the estimated fuel use in its place.
        label(fh * 0.4f)
        c.drawText(if (fuel != null) "FUEL" else "ECONOMY", rb.left, rb.top + fh * 0.5f, text)
        val valueText = if (fuel != null) "${fuel.roundToInt()}%" else units.economyText(s.fuelLper100, s.fuelLph)
        val frac = if (fuel != null) fuel / 100f else (s.fuelLper100?.div(20f) ?: s.fuelLph?.div(6f))
        number(fh * 0.55f, if (valueText == "–") Colors.DIM else Colors.FG, Paint.Align.RIGHT)
        c.drawText(valueText, rb.right, rb.top + fh * 0.55f, text)
        val fuelColor = when { fuel == null -> Colors.DIM; fuel < 12.5f -> Colors.RED; fuel < 25f -> Colors.SAND; else -> Colors.GOOD }
        segments(c, RectF(rb.left, rb.top + fh * 0.75f, rb.right, rb.top + fh * 1.0f), frac, 12) { f -> if (fuel != null) fuelColor else rampColor(f) }
        tires(c, RectF(rb.left, rb.top + rb.height() * 0.4f, rb.right, rb.bottom), s, units)
    }

    private fun box(c: Canvas, b: RectF, name: String, value: String, color: Int, tint: Int) {
        val h = b.height()
        fill.color = Colors.BOX
        c.drawRoundRect(b, h * 0.12f, h * 0.12f, fill)
        val inset = b.width() * 0.08f
        fill.color = tint
        c.drawCircle(b.left + inset + h * 0.05f, b.top + h * 0.24f, h * 0.05f, fill)
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
