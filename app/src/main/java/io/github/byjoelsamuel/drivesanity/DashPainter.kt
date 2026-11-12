package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.byjoelsamuel.drivesanity.map.MapPainter
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.SpeedLimit
import kotlin.math.min
import kotlin.math.roundToInt

/** A quiet palette: warm off-white on graphite, one dusty blue, and earthy warning colours. */
object Colors {
    val BG = Color.rgb(18, 20, 22)
    val FG = Color.rgb(232, 230, 225)
    val DIM = Color.rgb(128, 132, 138)
    val GRID = Color.rgb(43, 47, 51)
    val BOX = Color.rgb(26, 29, 32)
    val ACCENT = Color.rgb(143, 179, 194)   // dusty blue: the g-meter dot, a cold engine
    val GOOD = Color.rgb(143, 174, 139)     // sage: OBD2 connected
    val SAND = Color.rgb(212, 181, 119)
    val CLAY = Color.rgb(208, 139, 99)
    val RED = Color.rgb(201, 96, 90)
    val RED_DIM = Color.rgb(59, 42, 42)
    val REC = RED

    // Quiet tints that tell the four boxes apart.
    val COOLANT = Color.rgb(160, 140, 184)
    val INTAKE = Color.rgb(201, 138, 138)
    val BATTERY = ACCENT
    val TRIP = GOOD

    fun brake(level: BrakeLevel) = when (level) {
        BrakeLevel.NONE -> DIM
        BrakeLevel.LIGHT -> Color.rgb(168, 181, 154)
        BrakeLevel.MODERATE -> SAND
        BrakeLevel.HARD -> CLAY
        BrakeLevel.VERY_HARD -> RED
    }

    /** Off-white, fading to sand, then red at the redline. */
    fun rpm(rpm: Float): Int {
        val h = Revs.heat(rpm)
        return if (h <= 1f) mix(FG, SAND, h) else mix(SAND, RED, h - 1f)
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
 * Wide screens (the car, a phone on its side): braking, a big speed, the RPM bar and four
 * info boxes stacked on the left; a small g-meter and the fuel meter on the right. Tall
 * screens: the same stack with fuel and the g-meter underneath.
 */
class DashPainter(context: Context) {
    private val numbers = Fonts.semibold(context)
    private val labels = Fonts.medium(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = numbers }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    // Labels in Poppins Medium with a little tracking; numbers in Poppins SemiBold.
    private fun asLabel() {
        text.typeface = labels
        text.letterSpacing = 0.06f
    }

    private fun asNumber() {
        text.typeface = numbers
        text.letterSpacing = 0f
    }

    private val map = MapPainter(labels, numbers)
    private val retro = RetroPainter(context)
    private val cluster = ClusterPainter(context)
    private val diag = DiagPainter(context)

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units, layout: Prefs.Layout = Prefs.Layout.GAUGES) {
        if (layout == Prefs.Layout.RETRO) return retro.draw(c, r, s, units)
        if (layout == Prefs.Layout.DIAG) return diag.draw(c, r, s, units)
        if (r.width() > r.height() * 1.2f) {
            if (layout == Prefs.Layout.MAP) cluster.drawMap(c, r, s, units) else cluster.drawGauges(c, r, s, units)
            return
        }
        fill.color = Colors.BG
        c.drawRect(r, fill)
        if (layout == Prefs.Layout.MAP) drawMapTall(c, r, s, units) else drawTall(c, r, s, units)
    }


    /** The map screen on a tall phone: map on top, then gear and speed, brake, RPM, boxes, fuel. */
    private fun drawMapTall(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val pad = r.width() * 0.05f
        val inner = RectF(r.left + pad, r.top + pad * 0.5f, r.right - pad, r.bottom - pad * 0.5f)
        val h = inner.height()
        var y = inner.top
        fun row(frac: Float) = RectF(inner.left, y, inner.right, y + h * frac).also { y += h * frac }
        drawStatus(c, row(0.03f), s)
        row(0.01f)
        val mapRow = row(0.42f)
        if (Nav.route != null || Nav.message != null) {
            val boxH = mapRow.height() * 0.3f
            map.drawDirections(c, RectF(mapRow.left, mapRow.top, mapRow.right, mapRow.top + boxH), units)
            mapRow.top += boxH + 8f
        }
        map.draw(c, mapRow, s, units)
        row(0.02f)
        val top = row(0.17f)
        drawGear(c, RectF(top.left, top.top, top.left + top.width() * 0.35f, top.bottom), s)
        drawSpeed(c, RectF(top.left + top.width() * 0.35f, top.top, top.right, top.bottom), s, units)
        drawBrake(c, row(0.08f), s)
        drawRpm(c, row(0.10f), s)
        row(0.01f)
        drawBoxes(c, row(0.08f), s, units)
        row(0.01f)
        drawFuel(c, row(0.07f), s)
    }

    /** The big gear number in the middle of the map screen. */
    private fun drawGear(c: Canvas, b: RectF, s: Snapshot) {
        asLabel()
        text.textSize = min(b.height() * 0.11f, b.width() * 0.06f)
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("GEAR", b.left, b.top + text.textSize, text)
        asNumber()
        // Unknown: a small dash rather than a giant bar.
        text.textSize = min(b.height() * 0.95f, b.width() * 0.8f) * (if (s.gear == null) 0.45f else 1f)
        text.textAlign = Paint.Align.CENTER
        text.color = if (s.gear == null) Colors.GRID else Colors.FG
        c.drawText(s.gear?.toString() ?: "–", b.centerX(), b.top + b.height() * 0.86f, text)
    }




    private fun drawTall(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val pad = r.width() * 0.05f
        val inner = RectF(r.left + pad, r.top + pad * 0.5f, r.right - pad, r.bottom - pad * 0.5f)
        val h = inner.height()
        var y = inner.top
        fun row(frac: Float) = RectF(inner.left, y, inner.right, y + h * frac).also { y += h * frac }
        drawStatus(c, row(0.035f), s)
        drawBrake(c, row(0.10f), s)
        drawSpeed(c, row(0.20f), s, units)
        drawRpm(c, row(0.11f), s)
        row(0.01f)
        drawBoxes(c, row(0.08f), s, units)
        row(0.015f)
        drawFuel(c, row(0.07f), s)
        row(0.015f)
        drawGMeter(c, RectF(inner.left, y, inner.right, inner.bottom), s)
    }

    /** GPS state on the left, REC on the right. */
    private fun drawStatus(c: Canvas, b: RectF, s: Snapshot, showRec: Boolean = true, widthFrac: Float = 0.045f) {
        asLabel()
        text.textSize = min(b.height() * 0.8f, b.width() * widthFrac)
        text.textAlign = Paint.Align.LEFT
        text.color = if (s.accuracyM != null) Colors.DIM else Colors.REC
        val gps = when {
            !s.gpsOn -> "NO GPS ACCESS"
            s.accuracyM == null -> "WAITING FOR GPS"
            else -> "GPS ±${s.accuracyM.roundToInt()} m"
        }
        val base = b.bottom - b.height() * 0.2f
        c.drawText(gps, b.left, base, text)
        val x = b.left + text.measureText(gps + "   ")
        text.color = if (s.obdLive) Colors.GOOD else Colors.DIM
        c.drawText(if (s.obdLive) "OBD2 ●" else "NO OBD2", x, base, text)
        if (s.recording && showRec) {
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
        val speed = s.speedMps?.let { units.speed(it).roundToInt().toString() } ?: "–"
        val numSize = if (s.speedMps == null) big * 0.5f else big
        text.textAlign = Paint.Align.LEFT
        asNumber()
        text.textSize = numSize
        val numW = text.measureText(speed)
        asLabel()
        text.textSize = unitSize
        val gap = big * 0.06f
        val unitW = text.measureText(units.label)
        val x = b.centerX() - (numW + gap + unitW) / 2f

        asNumber()
        text.textSize = numSize
        // More than 5 over the posted limit (when one is known): the number turns clay.
        val over = s.speedMps != null && SpeedLimit.current?.let { l ->
            if (units == Units.MPH) units.speed(s.speedMps) > l.inMph() + 5 else units.speed(s.speedMps) > l.inKmh() + 8
        } == true
        text.color = when {
            s.speedMps == null -> Colors.DIM
            over -> Colors.CLAY
            else -> Colors.FG
        }
        c.drawText(speed, x, base, text)
        asLabel()
        text.textSize = unitSize
        text.color = Colors.DIM
        c.drawText(units.label, x + numW + gap, base, text)

        val other = units.other
        val second = s.speedMps?.let { other.speed(it).roundToInt().toString() } ?: "–"
        text.textAlign = Paint.Align.CENTER
        text.textSize = small
        c.drawText("$second ${other.label}", b.centerX(), base + small * 1.35f, text)
    }

    private fun drawBrake(c: Canvas, b: RectF, s: Snapshot, widthFrac: Float = 0.07f) {
        val brake = s.g?.brake ?: 0f
        val level = BrakeLevel.of(brake)
        val size = min(b.height() * 0.36f, b.width() * widthFrac)

        asLabel()
        text.textSize = size * 0.7f
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("BRAKE", b.left, b.top + size, text)

        asNumber()
        text.textSize = size
        text.textAlign = Paint.Align.RIGHT
        text.color = Colors.brake(level)
        val readout = when {
            s.g == null -> "–"
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
     * A segmented bar, 0 to [Revs.MAX], one segment per 250 rpm. Lit segments go off-white,
     * then sand, then red from the redline; unlit ones past the redline stay dark red.
     */
    private fun drawRpm(c: Canvas, b: RectF, s: Snapshot) {
        val rpm = s.rpm
        val size = min(b.height() * 0.30f, b.width() * 0.07f)

        asLabel()
        text.textSize = size * 0.7f
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("RPM", b.left, b.top + size, text)
        text.textAlign = Paint.Align.RIGHT
        if (rpm == null) {
            c.drawText(if (s.obdLive) "–" else "no OBD2", b.right, b.top + size, text)
        } else {
            asNumber()
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

        asLabel()
        text.textSize = labelSize
        text.textAlign = Paint.Align.CENTER
        for (k in 0..(Revs.MAX / 1000f).toInt()) {
            val x = (b.left + k * 1000f / Revs.MAX * b.width()).coerceIn(b.left + labelSize * 0.3f, b.right - labelSize * 0.3f)
            text.color = if (k * 1000f >= Revs.REDLINE) Colors.RED else Colors.DIM
            c.drawText(k.toString(), x, barTop + barH + labelSize * 1.15f, text)
        }
    }

    private data class Box(val label: String, val value: String, val color: Int = Colors.FG, val tint: Int = Colors.DIM)

    /** Four small boxes: coolant, intake air, battery, trip. */
    private fun drawBoxes(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        fun temp(cel: Float?): String {
            cel ?: return "–"
            return if (units == Units.MPH) "${(cel * 9 / 5 + 32).roundToInt()}°F" else "${cel.roundToInt()}°C"
        }
        val coolantColor = when {
            s.coolantC == null -> Colors.FG
            s.coolantC < 60f -> Colors.ACCENT   // still cold: go easy on the revs
            s.coolantC > 105f -> Colors.RED    // running hot
            else -> Colors.FG
        }
        val voltColor = when {
            s.volts == null -> Colors.FG
            s.volts < 11.8f || s.volts > 15.2f -> Colors.RED
            s.volts < 12.2f -> Colors.SAND
            else -> Colors.FG
        }
        val boxes = listOf(
            Box("COOLANT", temp(s.coolantC), coolantColor, Colors.COOLANT),
            Box("INTAKE", temp(s.intakeC), tint = Colors.INTAKE),
            Box("BATTERY", s.volts?.let { "%.1f V".format(it) } ?: "–", voltColor, Colors.BATTERY),
            Box("TRIP", "${oneDecimal(units.distance(s.tripM))} ${units.distanceLabel}", tint = Colors.TRIP),
        )
        val gap = b.width() * 0.025f
        val w = (b.width() - gap * (boxes.size - 1)) / boxes.size
        val h = b.height()
        val labelSize = min(h * 0.24f, w * 0.14f)
        val valueSize = min(h * 0.42f, w * 0.24f)
        boxes.forEachIndexed { i, box ->
            val x = b.left + i * (w + gap)
            fill.color = Colors.BOX
            c.drawRoundRect(x, b.top, x + w, b.bottom, h * 0.12f, h * 0.12f, fill)
            val inset = w * 0.08f
            text.textAlign = Paint.Align.LEFT
            asLabel()
            text.textSize = labelSize
            text.color = Colors.DIM
            fill.color = box.tint
            c.drawCircle(x + inset + labelSize * 0.3f, b.top + h * 0.12f + labelSize * 0.62f, labelSize * 0.3f, fill)
            c.drawText(box.label, x + inset + labelSize * 0.9f, b.top + h * 0.12f + labelSize, text)
            asNumber()
            text.textSize = valueSize
            text.color = if (box.value == "–") Colors.DIM else box.color
            c.drawText(box.value, x + inset, b.bottom - h * 0.16f, text)
        }
    }

    /** Fuel left in the tank, E to F. Sand under a quarter, red under an eighth. */
    private fun drawFuel(c: Canvas, b: RectF, s: Snapshot) {
        val pct = s.fuelPct
        val size = min(b.height() * 0.36f, b.width() * 0.10f)
        val color = when {
            pct == null -> Colors.DIM
            pct < 12.5f -> Colors.RED
            pct < 25f -> Colors.SAND
            else -> Colors.FG
        }
        asLabel()
        text.textSize = size * 0.7f
        text.textAlign = Paint.Align.LEFT
        text.color = Colors.DIM
        c.drawText("FUEL", b.left, b.top + size, text)
        drawPump(c, b.left + text.measureText("FUEL ") + size * 0.1f, b.top + size, size * 0.62f)
        text.textAlign = Paint.Align.RIGHT
        when {
            !s.fuelSupported -> c.drawText("car doesn't report it", b.right, b.top + size, text)
            pct == null -> c.drawText("–", b.right, b.top + size, text)
            else -> {
                asNumber()
                text.textSize = size
                text.color = color
                c.drawText("${pct.roundToInt()}%", b.right, b.top + size, text)
            }
        }

        asLabel()
        val label = size * 0.6f
        text.textSize = label
        val eW = text.measureText("E ")
        val barTop = b.top + size * 1.45f
        val barH = min(size * 0.75f, b.bottom - barTop)
        if (barH <= 0f) return
        val track = RectF(b.left + eW, barTop, b.right - eW, barTop + barH)
        text.color = Colors.DIM
        text.textAlign = Paint.Align.LEFT
        c.drawText("E", b.left, track.centerY() + label * 0.36f, text)
        text.textAlign = Paint.Align.RIGHT
        c.drawText("F", b.right, track.centerY() + label * 0.36f, text)
        fill.color = Colors.GRID
        c.drawRoundRect(track, barH / 2, barH / 2, fill)
        if (pct != null && pct > 0f) {
            fill.color = color
            val right = track.left + track.width() * (pct / 100f).coerceIn(0f, 1f)
            c.drawRoundRect(RectF(track.left, track.top, right, track.bottom), barH / 2, barH / 2, fill)
        }
        stroke.color = Colors.BG
        stroke.strokeWidth = barH * 0.1f
        for (q in listOf(0.25f, 0.5f, 0.75f)) {
            val x = track.left + track.width() * q
            c.drawLine(x, track.top, x, track.bottom, stroke)
        }
    }

    /** A small fuel-pump outline sitting on [baseline], [h] tall. */
    private fun drawPump(c: Canvas, x: Float, baseline: Float, h: Float) {
        stroke.color = Colors.DIM
        stroke.strokeWidth = h * 0.12f
        val w = h * 0.6f
        c.drawRoundRect(x, baseline - h, x + w, baseline, h * 0.08f, h * 0.08f, stroke)
        c.drawLine(x + w * 0.2f, baseline - h * 0.62f, x + w * 0.8f, baseline - h * 0.62f, stroke)
        c.drawLine(x + w, baseline - h * 0.75f, x + w * 1.35f, baseline - h * 0.55f, stroke)
        c.drawLine(x + w * 1.35f, baseline - h * 0.55f, x + w * 1.35f, baseline - h * 0.15f, stroke)
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

        asLabel()
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

        fill.color = Colors.ACCENT
        s.trail.forEachIndexed { i, g ->
            fill.alpha = 30 + 150 * i / maxOf(1, s.trail.size)
            val (x, y) = px(g)
            c.drawCircle(x, y, radius * 0.03f, fill)
        }
        fill.alpha = 255

        val g = s.g ?: return
        val level = BrakeLevel.of(g.brake)
        fill.color = if (level == BrakeLevel.NONE) Colors.ACCENT else Colors.brake(level)
        val (x, y) = px(g)
        c.drawCircle(x, y, radius * 0.09f, fill)

        asNumber()
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

/** Poppins, bundled in res/font (SIL Open Font License, see licenses/Poppins-OFL.txt). */
object Fonts {
    @Volatile private var medium: Typeface? = null
    @Volatile private var semibold: Typeface? = null

    fun medium(ctx: Context): Typeface = medium ?: load(ctx, R.font.poppins_medium).also { medium = it }
    fun semibold(ctx: Context): Typeface = semibold ?: load(ctx, R.font.poppins_semibold).also { semibold = it }

    private fun load(ctx: Context, id: Int): Typeface =
        runCatching { ResourcesCompat.getFont(ctx.applicationContext, id) }.getOrNull() ?: Typeface.DEFAULT_BOLD
}
