package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import androidx.core.graphics.PathParser
import io.github.byjoelsamuel.drivesanity.map.MapPainter
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import io.github.byjoelsamuel.drivesanity.nav.Nav
import kotlin.math.max
import kotlin.math.min

/**
 * The Music screen: the map on one side and what's playing on the other, and nothing else. The player
 * shows the cover in a rounded square, the song and artist, how far through it is, previous, play or
 * pause and next, and the music app's icon in the corner. Which side it sits on is a setting (and the
 * swap button on the card flips it).
 */
class MusicPainter(context: Context) {
    enum class Control { PREV, PLAY, NEXT, SWAP }

    private val app = context.applicationContext
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val hits = LinkedHashMap<Control, RectF>()

    // 24 x 24 icon shapes (Material's).
    private val prevGlyph = glyph("M6,6h2v12H6zM9.5,12l8.5,6V6z")
    private val nextGlyph = glyph("M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")
    private val playGlyph = glyph("M8,5v14l11,-7z")
    private val pauseGlyph = glyph("M6,19h4V5H6v14zM14,5v14h4V5h-4z")
    private val swapGlyph = glyph("M6.99,11L3,15l3.99,4v-3H14v-2H6.99v-3zM21,9l-3.99,-4v3H10v2h7.01v3L21,9z")
    private val noteGlyph = glyph("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z")

    private fun glyph(d: String): Path = PathParser.createPathFromPathData(d) ?: Path()

    /** Which button is at this point on the surface, if any. */
    fun hit(x: Float, y: Float): Control? = hits.entries.firstOrNull { it.value.contains(x, y) }?.key

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units, map: MapPainter) {
        MediaWatcher.poll(app)
        fill.shader = null
        fill.color = Colors.BG
        c.drawRect(r, fill)
        hits.clear()

        val pad = r.height() * 0.03f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val gap = inner.width() * 0.025f
        val playerW = inner.width() * 0.42f
        val left = Prefs.musicLeft(app)
        val player = if (left) RectF(inner.left, inner.top, inner.left + playerW, inner.bottom)
        else RectF(inner.right - playerW, inner.top, inner.right, inner.bottom)
        val mapBox = if (left) RectF(player.right + gap, inner.top, inner.right, inner.bottom)
        else RectF(inner.left, inner.top, player.left - gap, inner.bottom)

        // The map fades into the background at its edges, so it can run a little under the gap.
        val reach = gap * 0.4f
        map.draw(c, if (left) RectF(mapBox.left - reach, mapBox.top, mapBox.right, mapBox.bottom) else RectF(mapBox.left, mapBox.top, mapBox.right + reach, mapBox.bottom), s, units)
        if (Nav.route != null || Nav.message != null) {
            map.drawDirections(c, RectF(mapBox.left, mapBox.top, mapBox.right, mapBox.top + inner.height() * 0.3f), units, 238)
        }
        card(c, player, left)
    }

    // ---------------------------------------------------------------------------------------

    private fun card(c: Canvas, b: RectF, onLeft: Boolean) {
        val np = MediaWatcher.now
        val h = b.height()
        val w = b.width()
        val p = h * 0.05f
        fill.color = Colors.BOX
        c.drawRoundRect(b, h * 0.07f, h * 0.07f, fill)
        val accent = np?.accent ?: Colors.ACCENT

        // A wash of the cover's colour behind it all, very faint.
        if (np != null) {
            fill.color = (accent and 0x00FFFFFF) or (0x1C shl 24)
            c.drawRoundRect(b, h * 0.07f, h * 0.07f, fill)
        }

        // The corner buttons: swap sides at one, the music app's icon at the other.
        val corner = h * 0.095f
        val swapRect = RectF(b.left + p, b.top + p, b.left + p + corner, b.top + p + corner)
        glyphIn(c, swapGlyph, swapRect, Colors.DIM)
        hits[Control.SWAP] = RectF(swapRect).apply { inset(-p, -p) }
        val iconRect = RectF(b.right - p - corner, b.top + p, b.right - p, b.top + p + corner)
        if (np?.appIcon != null) {
            clipRound(c, iconRect, corner * 0.24f) { c.drawBitmap(np.appIcon, null, iconRect, bmpPaint) }
        } else {
            fill.color = Colors.GRID
            c.drawRoundRect(iconRect, corner * 0.24f, corner * 0.24f, fill)
            glyphIn(c, noteGlyph, RectF(iconRect).apply { inset(corner * 0.2f, corner * 0.2f) }, Colors.DIM)
        }
        label(h * 0.05f, Colors.DIM, Paint.Align.CENTER)
        c.drawText(np?.appName?.uppercase()?.take(22) ?: "NOW PLAYING", b.centerX(), b.top + p + corner * 0.72f, text)

        // The cover, in a rounded square.
        val art = min(h * 0.46f, w * 0.4f)
        val artRect = RectF(b.left + p, b.top + h * 0.19f, b.left + p + art, b.top + h * 0.19f + art)
        val radius = art * 0.14f
        if (np?.art != null) {
            clipRound(c, artRect, radius) { c.drawBitmap(np.art, null, artRect, bmpPaint) }
        } else {
            fill.color = Colors.GRID
            c.drawRoundRect(artRect, radius, radius, fill)
            glyphIn(c, noteGlyph, RectF(artRect).apply { inset(art * 0.28f, art * 0.28f) }, Colors.DIM)
        }

        // The words, to the right of the cover.
        val tx = artRect.right + p
        val tw = b.right - p - tx
        if (np == null) {
            number(h * 0.075f, Colors.FG)
            text.textAlign = Paint.Align.LEFT
            c.drawText(fit(if (MediaWatcher.access) "Nothing playing" else "Allow notification access", tw), tx, artRect.top + h * 0.1f, text)
            label(h * 0.048f, Colors.DIM)
            if (!MediaWatcher.access) {
                lines(c, "Setup → Music on the phone", tx, artRect.top + h * 0.17f, tw)
            } else {
                lines(c, "Start a song in YouTube Music or any music app.", tx, artRect.top + h * 0.17f, tw)
            }
        } else {
            number(h * 0.08f, Colors.FG)
            c.drawText(fit(np.title, tw), tx, artRect.top + h * 0.1f, text)
            label(h * 0.058f, Colors.FG)
            c.drawText(fit(np.artist, tw), tx, artRect.top + h * 0.19f, text)
            if (np.album.isNotEmpty()) {
                label(h * 0.046f, Colors.DIM)
                c.drawText(fit(np.album, tw), tx, artRect.top + h * 0.26f, text)
            }
            // How far through the song.
            val barY = artRect.bottom - h * 0.075f
            val bar = RectF(tx, barY, tx + tw, barY + h * 0.016f)
            fill.color = Colors.GRID
            c.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, fill)
            if (np.durationMs > 0) {
                val f = (np.positionNow().toFloat() / np.durationMs).coerceIn(0f, 1f)
                fill.color = accent
                c.drawRoundRect(RectF(bar.left, bar.top, bar.left + max(bar.height(), bar.width() * f), bar.bottom), bar.height() / 2, bar.height() / 2, fill)
                label(h * 0.044f, Colors.DIM)
                c.drawText(clock(np.positionNow()), tx, barY + h * 0.07f, text)
                label(h * 0.044f, Colors.DIM, Paint.Align.RIGHT)
                c.drawText(clock(np.durationMs), tx + tw, barY + h * 0.07f, text)
            }
        }

        // Previous, play or pause, next.
        val cy = b.bottom - h * 0.155f
        val small = h * 0.2f
        val big = h * 0.25f
        val space = h * 0.05f
        val total = small + big + small + 2 * space
        var x = b.centerX() - total / 2
        val prev = RectF(x, cy - small / 2, x + small, cy + small / 2); x += small + space
        val play = RectF(x, cy - big / 2, x + big, cy + big / 2); x += big + space
        val next = RectF(x, cy - small / 2, x + small, cy + small / 2)
        val can = np != null
        key(c, prev, prevGlyph, false, accent, can && np?.canPrev != false)
        key(c, play, if (np?.playing == true) pauseGlyph else playGlyph, true, accent, can)
        key(c, next, nextGlyph, false, accent, can && np?.canNext != false)
        hits[Control.PREV] = RectF(prev).apply { inset(-space / 2, -space) }
        hits[Control.PLAY] = RectF(play).apply { inset(-space / 2, -space) }
        hits[Control.NEXT] = RectF(next).apply { inset(-space / 2, -space) }
    }

    /** A rounded button with a symbol in it; the main one is filled with the cover's colour. */
    private fun key(c: Canvas, b: RectF, g: Path, main: Boolean, accent: Int, enabled: Boolean) {
        fill.color = if (main && enabled) accent else Colors.GRID
        c.drawRoundRect(b, b.height() * 0.3f, b.height() * 0.3f, fill)
        val inset = b.width() * if (main) 0.27f else 0.25f
        glyphIn(c, g, RectF(b).apply { inset(inset, inset) }, if (main && enabled) Colors.BG else if (enabled) Colors.FG else Colors.DIM)
    }

    private fun glyphIn(c: Canvas, g: Path, b: RectF, color: Int) {
        fill.color = color
        c.save()
        c.translate(b.left, b.top)
        c.scale(b.width() / 24f, b.height() / 24f)
        c.drawPath(g, fill)
        c.restore()
    }

    private inline fun clipRound(c: Canvas, b: RectF, radius: Float, draw: () -> Unit) {
        c.save()
        clip.reset()
        clip.addRoundRect(b, radius, radius, Path.Direction.CW)
        c.clipPath(clip)
        draw()
        c.restore()
    }

    private fun fit(s: String, width: Float): String = TextUtils.ellipsize(s, android.text.TextPaint(text), width, TextUtils.TruncateAt.END).toString()

    /** A couple of short lines of small text, broken on spaces to fit. */
    private fun lines(c: Canvas, s: String, x: Float, y: Float, width: Float) {
        var line = ""
        var yy = y
        for (word in s.split(' ')) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (text.measureText(t) > width && line.isNotEmpty()) {
                c.drawText(line, x, yy, text)
                yy += text.textSize * 1.3f
                line = word
            } else line = t
        }
        if (line.isNotEmpty()) c.drawText(line, x, yy, text)
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = labels; text.letterSpacing = 0.04f; text.textSize = size; text.color = color; text.textAlign = align
    }

    private fun number(size: Float, color: Int = Colors.FG, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = numbers; text.letterSpacing = 0f; text.textSize = size; text.color = color; text.textAlign = align
    }
}
