package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pictures of a drive, a 0–60 run or all your drives together, made to post: a tall one
 * for Stories and a 4:5 one for the feed. They're drawn here, with no server, in the same look as the
 * dashboards, with an optional photo behind them.
 *
 * The blocks on a picture are grouped by genre, each genre in one panel (see [Genre]), and the
 * panels are stacked top to bottom: if there's more than fits, the stack is shrunk to fit rather than
 * cut off.
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
    private const val M = 72f   // side margin
    private const val PAD = 44f // inside a panel
    private const val GAP = 26f // between panels
    private const val GENRE_H = 62f

    /** The panels, in the order they're stacked. */
    private val ORDER = listOf(Genre.TRIP, Genre.ROUTE, Genre.SPEED, Genre.PERFORMANCE, Genre.ENGINE, Genre.STANDING)

    private class Pens(ctx: Context, val onPhoto: Boolean) {
        val numbers = Fonts.dashNumbers(ctx)
        val labels = Fonts.dashLabels(ctx)
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

        /** On a photo the text gets a soft shadow so it stays readable on bright parts. */
        private fun shadow() {
            if (onPhoto) text.setShadowLayer(10f, 0f, 2f, android.graphics.Color.argb(170, 0, 0, 0)) else text.clearShadowLayer()
        }

        fun label(size: Float, color: Int = if (onPhoto) android.graphics.Color.rgb(190, 195, 202) else Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
            text.typeface = labels; text.letterSpacing = 0.12f; text.textSize = size; text.color = color; text.textAlign = align; shadow()
        }

        fun number(size: Float, color: Int = PALE, align: Paint.Align = Paint.Align.LEFT) {
            text.typeface = numbers; text.letterSpacing = 0f; text.textSize = size; text.color = color; text.textAlign = align; shadow()
        }
    }

    private class Panel(val genre: Genre, val height: Float, val draw: (Canvas, Float, Float, Float) -> Unit)

    fun render(ctx: Context, data: CardData, config: ShareConfig, format: Format, background: Bitmap?): Bitmap {
        val p = Pens(ctx, onPhoto = background != null)
        val bmp = Bitmap.createBitmap(format.w, format.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = format.w.toFloat()
        val h = format.h.toFloat()
        val story = format == Format.STORY
        backdrop(c, p, background, config.dim, w, h)

        // The top: the app's name, the date, then the title and car.
        p.label(32f)
        c.drawText("DRIVESANITY", M, 120f, p.text)
        if (Block.DATE in config.blocks) {
            p.label(32f, align = Paint.Align.RIGHT)
            c.drawText(data.whenText, w - M, 120f, p.text)
        }
        var top = 150f
        if (Block.TITLE in config.blocks) {
            p.number(if (story) 92f else 80f)
            c.drawText(data.title.take(22), M, top + 90f, p.text)
            top += 118f
        }
        if (Block.CAR in config.blocks) {
            p.label(34f)
            c.drawText(data.car, M, top + 30f, p.text)
            top += 60f
        }
        top += 26f

        val panels = ORDER.mapNotNull { panel(p, data, config, it, format, w - 2 * M) }
        val avail = (h - 130f) - top
        val total = panels.sumOf { it.height.toDouble() }.toFloat() + GAP * max(0, panels.size - 1)
        val k = if (total > avail && total > 0f) avail / total else 1f
        val used = total * k
        var y = top + when (config.place) {
            Place.TOP -> 0f
            Place.MIDDLE -> (avail - used) / 2
            Place.BOTTOM -> avail - used
        }
        c.save()
        c.translate(M, y)
        c.scale(k, k)
        var yy = 0f
        for (panel in panels) {
            backing(c, p, config.panel, background != null, RectF(0f, yy, (w - 2 * M) / k, yy + panel.height))
            panel.draw(c, 0f, yy, (w - 2 * M) / k)
            yy += panel.height + GAP
        }
        c.restore()

        p.label(30f, align = Paint.Align.CENTER)
        c.drawText("MADE WITH DRIVESANITY", w / 2f, h - 70f, p.text)
        return bmp
    }

    // ---------------------------------------------------------------------------------------
    // Background and panels
    // ---------------------------------------------------------------------------------------

    /** The photo (cropped to fill) and a dark layer over it, or the plain dark gradient. */
    private fun backdrop(c: Canvas, p: Pens, photo: Bitmap?, dim: Float, w: Float, h: Float) {
        if (photo == null) {
            p.fill.shader = LinearGradient(0f, 0f, 0f, h, Colors.BG, android.graphics.Color.rgb(10, 11, 13), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, p.fill)
            p.fill.shader = null
            return
        }
        val scale = max(w / photo.width, h / photo.height)
        val dw = photo.width * scale
        val dh = photo.height * scale
        c.drawBitmap(photo, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        p.fill.color = android.graphics.Color.argb((dim * 255).roundToInt().coerceIn(0, 255), 0, 0, 0)
        c.drawRect(0f, 0f, w, h, p.fill)
    }

    private fun backing(c: Canvas, p: Pens, style: PanelStyle, onPhoto: Boolean, r: RectF) {
        when (style) {
            PanelStyle.NONE -> return
            PanelStyle.SOLID -> p.fill.color = Colors.BOX
            PanelStyle.GLASS -> p.fill.color = if (onPhoto) android.graphics.Color.argb(150, 8, 9, 11) else Colors.BOX
        }
        c.drawRoundRect(r, 40f, 40f, p.fill)
    }

    // ---------------------------------------------------------------------------------------
    // One panel per genre
    // ---------------------------------------------------------------------------------------

    private fun panel(p: Pens, d: CardData, cfg: ShareConfig, genre: Genre, f: Format, width: Float): Panel? {
        val blocks = Block.of(genre).filter { it in cfg.blocks && d.has(it) }
        if (blocks.isEmpty()) return null
        val story = f == Format.STORY
        val inner = width - 2 * PAD
        val title = genre.label.uppercase()
        fun frame(contentH: Float, draw: (Canvas, Float, Float, Float) -> Unit) = Panel(genre, PAD * 2 + GENRE_H + contentH) { c, x, y, w ->
            p.label(30f)
            c.drawText(title, x + PAD, y + PAD + 26f, p.text)
            draw(c, x + PAD, y + PAD + GENRE_H, w - 2 * PAD)
        }
        return when (genre) {
            Genre.TRIP -> {
                val items = blocks.map {
                    when (it) {
                        Block.DISTANCE -> "DISTANCE" to "${oneDecimal(d.units.distance(d.distanceM!!))} ${d.units.distanceLabel}"
                        Block.TIME -> "TIME" to hms(d.durationS!!)
                        else -> "DRIVES" to d.drives.toString()
                    }
                }
                val size = if (items.size >= 3) 66f else if (story) 100f else 88f
                frame(size + 56f) { c, x, y, w -> stats(c, p, x, y, w, items, size) }
            }
            Genre.ROUTE -> {
                val mapH = if (story) 700f else 420f
                frame(mapH) { c, x, y, w -> path(c, p, RectF(x, y, x + w, y + mapH), d.path, d.topKmh ?: 1f, d.pathHidden) }
            }
            Genre.SPEED -> {
                val items = blocks.map {
                    if (it == Block.TOP_SPEED) "TOP SPEED" to "${d.units.speed(d.topKmh!! / 3.6f).roundToInt()} ${d.units.label}"
                    else "AVERAGE" to "${d.units.speed(d.avgKmh!! / 3.6f).roundToInt()} ${d.units.label}"
                }
                frame(72f + 56f) { c, x, y, w -> stats(c, p, x, y, w, items, 72f) }
            }
            Genre.PERFORMANCE -> {
                val items = blocks.map {
                    when (it) {
                        Block.BRAKING -> "BRAKING" to "%.2f g".format(d.brakeG!!)
                        Block.ACCEL -> "ACCELERATION" to "%.2f g".format(d.accelG!!)
                        else -> "CORNERING" to "%.2f g".format(d.latG!!)
                    }
                }
                frame(60f + 56f) { c, x, y, w -> stats(c, p, x, y, w, items, 60f) }
            }
            Genre.ENGINE -> frame(72f + 56f) { c, x, y, w -> stats(c, p, x, y, w, listOf("HIGHEST REVS" to "%,d".format(d.maxRpm!!.roundToInt())), 72f) }
            Genre.STANDING -> {
                val rows = blocks.map {
                    if (it == Block.ZERO_60) "0–60 MPH" to sec(d.to60Ms) else "0–100 KM/H" to sec(d.to100Ms)
                }
                val rowH = 176f
                frame(rows.size * rowH) { c, x, y, w ->
                    for ((i, row) in rows.withIndex()) {
                        val ry = y + i * rowH
                        p.label(30f)
                        c.drawText(row.first, x, ry + 30f, p.text)
                        p.number(100f)
                        c.drawText(row.second, x, ry + 136f, p.text)
                    }
                }
            }
            else -> null
        }
    }

    private fun sec(ms: Long?) = if (ms == null) "–" else "%.2f s".format(java.util.Locale.US, ms / 1000.0)

    /** A row of labelled values, evenly spaced; each shrinks a little if it won't fit its column. */
    private fun stats(c: Canvas, p: Pens, x: Float, y: Float, w: Float, items: List<Pair<String, String>>, size: Float) {
        val col = w / items.size
        for ((i, item) in items.withIndex()) {
            val cx = x + i * col
            p.label(27f)
            c.drawText(item.first, cx, y + 24f, p.text)
            p.number(size)
            while (p.text.measureText(item.second) > col - 22f && p.text.textSize > 28f) p.text.textSize -= 3f
            c.drawText(item.second, cx, y + 24f + 20f + size * 0.82f, p.text)
        }
    }

    /** The path, fitted into [box] with the right proportions and coloured by speed, with a soft glow under it. */
    private fun path(c: Canvas, p: Pens, box: RectF, pts: List<SessionPoint>, topKmh: Float, hidden: Boolean) {
        if (pts.size < 2) {
            p.label(30f, align = Paint.Align.CENTER)
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
        val pad = 40f
        val scale = min((box.width() - 2 * pad) / spanX, (box.height() - 2 * pad - 20f) / spanY).toFloat()
        val offX = box.left + (box.width() - (spanX * scale).toFloat()) / 2
        val offYtop = box.top + (box.height() - 20f - (spanY * scale).toFloat()) / 2
        fun px(i: Int) = offX + ((xs[i] - minX) * scale).toFloat()
        fun py(i: Int) = offYtop + (((maxY - ys[i])) * scale).toFloat()
        val top = max(1f, topKmh)
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
        p.label(24f)
        c.drawText(if (hidden) "THE FIRST AND LAST 250 M ARE LEFT OUT" else "SLOW  →  FAST", box.left + 4f, box.bottom - 2f, p.text)
    }

    private fun speedColor(f: Float): Int = when {
        f < 0.35f -> Colors.GOOD
        f < 0.65f -> Colors.SAND
        f < 0.85f -> Colors.CLAY
        else -> Colors.RED
    }
}
