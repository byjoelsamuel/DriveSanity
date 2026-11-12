package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import io.github.byjoelsamuel.drivesanity.track.RunView
import io.github.byjoelsamuel.drivesanity.track.lapText
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pictures of a drive, your laps or a standing start, made to post: a tall one for Stories and a
 * 4:5 one for the feed. They're drawn here, with no server, in the same look as the dashboards.
 *
 * A picture of a path shows where you've been, and drives often start and end at home. So the first and
 * last 250 m of the path are left out unless the person chooses to show them. No coordinates, street
 * names or map tiles go on the card; it's just the shape of the drive.
 */
object ShareCard {
    enum class Format(val w: Int, val h: Int, val label: String) {
        STORY(1080, 1920, "Story"),
        POST(1080, 1350, "Post"),
    }

    const val HIDE_ENDS_M = 250.0

    private val PALE = android.graphics.Color.rgb(222, 228, 236)
    private const val M = 72f // side margin

    private class Pens(ctx: Context) {
        val numbers = Fonts.dashNumbers(ctx)
        val labels = Fonts.dashLabels(ctx)
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

        fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
            text.typeface = labels; text.letterSpacing = 0.12f; text.textSize = size; text.color = color; text.textAlign = align
        }

        fun number(size: Float, color: Int = PALE, align: Paint.Align = Paint.Align.LEFT) {
            text.typeface = numbers; text.letterSpacing = 0f; text.textSize = size; text.color = color; text.textAlign = align
        }
    }

    // ---------------------------------------------------------------------------------------
    // A drive
    // ---------------------------------------------------------------------------------------

    fun drive(ctx: Context, session: Session, title: String, whenText: String, units: Units, format: Format, hideEnds: Boolean): Bitmap {
        val p = Pens(ctx)
        val (bmp, c) = canvas(format, p)
        val story = format == Format.STORY
        header(c, p, format, title, whenText, ctx)
        val mapH = if (story) 800f else 400f
        val top = if (story) 330f else 290f
        val box = RectF(M, top, format.w - M, top + mapH)
        pathBox(c, p, box, if (hideEnds) session.trimmedPath(HIDE_ENDS_M) else session.path, session.topKmh, hideEnds)

        var y = box.bottom + if (story) 175f else 135f
        // Distance and time, the big pair.
        val half = (format.w - 2 * M) / 2
        pair(c, p, M, y, half, "DISTANCE", "${oneDecimal(units.distance(session.distanceM))} ${units.distanceLabel}", if (story) 112f else 96f)
        pair(c, p, M + half, y, half, "TIME", hms(session.durationS), if (story) 112f else 96f)
        y += if (story) 235f else 190f
        val third = (format.w - 2 * M) / 3
        val s1 = if (story) 60f else 54f
        small(c, p, M, y, third, "TOP SPEED", "${units.speed(session.topKmh / 3.6f).roundToInt()} ${units.label}", s1)
        small(c, p, M + third, y, third, "AVERAGE", "${units.speed(session.avgMovingKmh / 3.6f).roundToInt()} ${units.label}", s1)
        small(c, p, M + 2 * third, y, third, "HIGHEST REVS", if (session.maxRpm > 0) "%,d".format(session.maxRpm.roundToInt()) else "–", s1)
        y += if (story) 185f else 150f
        small(c, p, M, y, third, "BRAKING", "%.2f g".format(session.peakBrakeG), s1)
        small(c, p, M + third, y, third, "ACCELERATION", "%.2f g".format(session.peakAccelG), s1)
        small(c, p, M + 2 * third, y, third, "CORNERING", "%.2f g".format(session.peakLatG), s1)
        footer(c, p, format)
        return bmp
    }

    // ---------------------------------------------------------------------------------------
    // Laps
    // ---------------------------------------------------------------------------------------

    fun laps(ctx: Context, session: Session, whenText: String, format: Format, hideEnds: Boolean): Bitmap {
        val p = Pens(ctx)
        val (bmp, c) = canvas(format, p)
        val story = format == Format.STORY
        val laps = session.laps
        val best = session.bestLap
        header(c, p, format, laps.firstOrNull()?.track ?: "Laps", whenText, ctx)

        val mapH = if (story) 520f else 330f
        val top = if (story) 330f else 290f
        val box = RectF(M, top, format.w - M, top + mapH)
        pathBox(c, p, box, if (hideEnds) session.trimmedPath(HIDE_ENDS_M) else session.path, session.topKmh, hideEnds)

        var y = box.bottom + if (story) 150f else 110f
        p.label(34f)
        c.drawText("BEST LAP", M, y - 125f, p.text)
        p.number(if (story) 170f else 140f, Colors.GOOD)
        c.drawText(lapText(best?.ms), M, y + 60f, p.text)
        p.label(34f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText("TOP SPEED", format.w - M, y - 125f, p.text)
        if (best != null) {
            p.number(60f, PALE, Paint.Align.RIGHT)
            c.drawText("${units(ctx).speed(best.topKmh / 3.6f).roundToInt()} ${units(ctx).label}", format.w - M, y + 60f, p.text)
        }
        y += if (story) 150f else 120f

        // Every lap, as a bar as long as it is slow: the best lap is the shortest, in green.
        val shown = if (story) 8 else 5
        val rows = laps.take(shown)
        val slowest = (rows.maxOfOrNull { it.ms } ?: 1L).toFloat()
        val fastest = (best?.ms ?: 1L).toFloat()
        val rowH = if (story) 112f else 92f
        for ((i, l) in rows.withIndex()) {
            val ry = y + i * rowH
            val isBest = l.ms == best?.ms
            p.label(32f, Colors.DIM)
            c.drawText("LAP ${l.number}", M, ry + 40f, p.text)
            p.number(48f, if (isBest) Colors.GOOD else PALE, Paint.Align.RIGHT)
            c.drawText(lapText(l.ms), format.w - M, ry + 46f, p.text)
            // The bar runs from the best lap's length to the slowest, so the gaps are visible.
            val barL = M + 190f
            val barR = format.w - M - 300f
            val frac = if (slowest > fastest) 0.35f + 0.65f * ((l.ms - fastest) / (slowest - fastest)) else 0.35f
            p.fill.color = Colors.GRID
            c.drawRect(barL, ry + 22f, barR, ry + 40f, p.fill)
            p.fill.color = if (isBest) Colors.GOOD else Colors.ACCENT
            c.drawRect(barL, ry + 22f, barL + (barR - barL) * frac, ry + 40f, p.fill)
        }
        p.label(30f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText("${laps.size} LAPS", format.w - M, y - 25f, p.text)
        if (laps.size > shown) {
            p.label(30f)
            c.drawText("+ ${laps.size - shown} more", M, y + shown * rowH + 20f, p.text)
        }
        footer(c, p, format)
        return bmp
    }

    private fun units(ctx: Context) = Prefs.units(ctx)

    // ---------------------------------------------------------------------------------------
    // A standing start
    // ---------------------------------------------------------------------------------------

    fun run(ctx: Context, run: RunView, whenText: String, units: Units, format: Format): Bitmap {
        val p = Pens(ctx)
        val (bmp, c) = canvas(format, p)
        val story = format == Format.STORY
        header(c, p, format, "Standing start", whenText, ctx)
        val rows = listOf(
            "0–60 MPH" to run.to60Ms?.let { "%.2f s".format(java.util.Locale.US, it / 1000.0) },
            "0–100 KM/H" to run.to100Ms?.let { "%.2f s".format(java.util.Locale.US, it / 1000.0) },
            "¼ MILE" to run.quarterMs?.let { "%.2f s".format(java.util.Locale.US, it / 1000.0) },
        )
        var y = if (story) 560f else 420f
        val gap = if (story) 330f else 260f
        for ((name, value) in rows) {
            p.label(40f)
            c.drawText(name, M, y - 130f, p.text)
            p.number(if (story) 200f else 160f, if (value == null) Colors.DIM else PALE)
            c.drawText(value ?: "–", M, y + 40f, p.text)
            if (name == "¼ MILE" && run.trapKmh != null) {
                p.label(32f)
                c.drawText("TRAP SPEED", M, y + 110f, p.text)
                p.number(52f, Colors.SAND, Paint.Align.RIGHT)
                c.drawText("${units.speed(run.trapKmh / 3.6f).roundToInt()} ${units.label}", format.w - M, y + 112f, p.text)
            }
            p.fill.color = Colors.GRID
            c.drawRect(M, y + 150f, format.w - M, y + 153f, p.fill)
            y += gap
        }
        footer(c, p, format)
        return bmp
    }

    // ---------------------------------------------------------------------------------------
    // Pieces
    // ---------------------------------------------------------------------------------------

    private fun canvas(format: Format, p: Pens): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(format.w, format.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        p.fill.shader = LinearGradient(0f, 0f, 0f, format.h.toFloat(), Colors.BG, android.graphics.Color.rgb(10, 11, 13), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, format.w.toFloat(), format.h.toFloat(), p.fill)
        p.fill.shader = null
        return bmp to c
    }

    private fun header(c: Canvas, p: Pens, format: Format, title: String, whenText: String, ctx: Context) {
        val story = format == Format.STORY
        p.label(32f)
        c.drawText("DRIVESANITY", M, if (story) 120f else 100f, p.text)
        p.label(32f, Colors.DIM, Paint.Align.RIGHT)
        c.drawText(whenText, format.w - M, if (story) 120f else 100f, p.text)
        p.number(if (story) 92f else 80f)
        c.drawText(title.take(22), M, if (story) 235f else 200f, p.text)
        p.label(34f)
        c.drawText(CarProfiles.byId(Prefs.carId(ctx)).name, M, if (story) 290f else 250f, p.text)
    }

    private fun footer(c: Canvas, p: Pens, format: Format) {
        p.label(30f, Colors.DIM, Paint.Align.CENTER)
        c.drawText("MADE WITH DRIVESANITY", format.w / 2f, format.h - 70f, p.text)
    }

    private fun pair(c: Canvas, p: Pens, x: Float, y: Float, w: Float, name: String, value: String, size: Float) {
        p.label(34f)
        c.drawText(name, x, y - size * 1.0f, p.text)
        p.number(size)
        // Shrink to fit the column rather than run into the next one.
        while (p.text.measureText(value) > w - 30f && p.text.textSize > 40f) p.text.textSize -= 4f
        c.drawText(value, x, y, p.text)
    }

    private fun small(c: Canvas, p: Pens, x: Float, y: Float, w: Float, name: String, value: String, size: Float) {
        p.label(28f)
        c.drawText(name, x, y - size - 8f, p.text)
        p.number(size)
        while (p.text.measureText(value) > w - 20f && p.text.textSize > 30f) p.text.textSize -= 3f
        c.drawText(value, x, y, p.text)
    }

    /** The path, fitted into [box] with the right proportions and coloured by speed, with a soft glow under it. */
    private fun pathBox(c: Canvas, p: Pens, box: RectF, pts: List<SessionPoint>, topKmh: Float, hidden: Boolean) {
        p.fill.color = Colors.BOX
        c.drawRoundRect(box, 44f, 44f, p.fill)
        if (pts.size < 2) {
            p.label(34f, Colors.DIM, Paint.Align.CENTER)
            c.drawText(if (hidden) "PATH HIDDEN FOR PRIVACY" else "NO PATH RECORDED", box.centerX(), box.centerY(), p.text)
            return
        }
        val lat0 = pts.map { it.lat!! }.average()
        val k = cos(Math.toRadians(lat0))
        val xs = pts.map { it.lon!! * k }
        val ys = pts.map { it.lat!! }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = max(maxX - minX, 1e-7)
        val spanY = max(maxY - minY, 1e-7)
        val pad = 70f
        val scale = min((box.width() - 2 * pad) / spanX, (box.height() - 2 * pad) / spanY).toFloat()
        val offX = box.left + (box.width() - (spanX * scale).toFloat()) / 2
        val offY = box.top + (box.height() - (spanY * scale).toFloat()) / 2
        fun px(i: Int) = offX + ((xs[i] - minX) * scale).toFloat()
        fun py(i: Int) = box.bottom - (box.height() - (spanY * scale).toFloat()) / 2 - ((ys[i] - minY) * scale).toFloat()
        val top = max(1f, topKmh)
        // Glow first, then the line itself.
        for (pass in 0..1) {
            p.line.strokeWidth = if (pass == 0) 26f else 9f
            for (i in 1 until pts.size) {
                if (pts[i].t - pts[i - 1].t > 30.0) continue
                val col = speedColor((pts[i].kmh ?: 0f) / top)
                p.line.color = if (pass == 0) (col and 0x00FFFFFF) or (0x33 shl 24) else col
                c.drawLine(px(i - 1), py(i - 1), px(i), py(i), p.line)
            }
        }
        if (!hidden) {
            p.fill.color = Colors.GOOD
            c.drawCircle(px(0), py(0), 16f, p.fill)
            p.fill.color = Colors.RED
            c.drawCircle(px(pts.size - 1), py(pts.size - 1), 16f, p.fill)
        }
        p.label(26f, Colors.DIM, Paint.Align.LEFT)
        c.drawText(if (hidden) "THE FIRST AND LAST 250 M ARE LEFT OUT" else "SLOW  →  FAST", box.left + 36f, box.bottom - 30f, p.text)
    }

    private fun speedColor(f: Float): Int = when {
        f < 0.35f -> Colors.GOOD
        f < 0.65f -> Colors.SAND
        f < 0.85f -> Colors.CLAY
        else -> Colors.RED
    }
}
