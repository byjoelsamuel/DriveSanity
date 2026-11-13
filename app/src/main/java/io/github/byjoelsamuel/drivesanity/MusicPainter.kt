package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.PathParser
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import kotlin.math.min

/**
 * The Music screen: what's playing and nothing else. The cover in a rounded square on the left, then the
 * app's icon and name, the song, the artist, the album, and how far through it is. There are no drawn
 * buttons: previous, play/pause and next are the car's own buttons down the right edge (set up in
 * DashScreen), because those are the ones the car's knob and screen can actually press. A strip is left
 * free at the right for them.
 */
class MusicPainter(context: Context) {
    private val app = context.applicationContext
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val noteGlyph = PathParser.createPathFromPathData("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z") ?: Path()

    fun draw(c: Canvas, r: RectF, @Suppress("UNUSED_PARAMETER") s: Snapshot, @Suppress("UNUSED_PARAMETER") units: Units) {
        MediaWatcher.poll(app)
        val np = MediaWatcher.now
        val h = r.height()
        val w = r.width()
        fill.shader = null
        fill.color = Colors.BG
        c.drawRect(r, fill)
        val accent = np?.accent ?: Colors.ACCENT

        // The room: the left edge, the strip kept free for the car's buttons at the right, and the top.
        val left = r.left + w * 0.05f
        val rightEdge = r.right - w * 0.13f
        val art = min(h * 0.74f, (rightEdge - left) * 0.36f)
        val artRect = RectF(left, r.top + (h - art) / 2 + h * 0.02f, left + art, r.top + (h - art) / 2 + h * 0.02f + art)

        // A faint wash of the cover's colour behind the cover.
        if (np != null) {
            fill.shader = RadialGradient(
                artRect.centerX(), artRect.centerY(), h * 0.95f,
                intArrayOf((accent and 0x00FFFFFF) or (0x34 shl 24), 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(r, fill)
            fill.shader = null
        }

        // The cover.
        val radius = art * 0.13f
        if (np?.art != null) {
            c.save()
            clip.reset()
            clip.addRoundRect(artRect, radius, radius, Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(np.art, null, artRect, bmpPaint)
            c.restore()
        } else {
            fill.color = Colors.BOX
            c.drawRoundRect(artRect, radius, radius, fill)
            glyph(c, noteGlyph, RectF(artRect).apply { inset(art * 0.3f, art * 0.3f) }, Colors.DIM)
        }

        // The words.
        val tx = artRect.right + w * 0.045f
        val tw = rightEdge - tx
        val top = artRect.top
        val icon = h * 0.09f
        if (np?.appIcon != null) {
            val ir = RectF(tx, top, tx + icon, top + icon)
            c.save()
            clip.reset()
            clip.addRoundRect(ir, icon * 0.24f, icon * 0.24f, Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(np.appIcon, null, ir, bmpPaint)
            c.restore()
        } else {
            fill.color = Colors.BOX
            c.drawRoundRect(RectF(tx, top, tx + icon, top + icon), icon * 0.24f, icon * 0.24f, fill)
            glyph(c, noteGlyph, RectF(tx, top, tx + icon, top + icon).apply { inset(icon * 0.2f, icon * 0.2f) }, Colors.DIM)
        }
        label(h * 0.05f)
        c.drawText((np?.appName ?: "NOW PLAYING").uppercase().take(26), tx + icon + w * 0.015f, top + icon * 0.68f, text)

        if (np == null) {
            number(h * 0.1f, Colors.FG)
            c.drawText(fit(if (MediaWatcher.access) "Nothing playing" else "Allow notification access", tw), tx, top + art * 0.46f, text)
            label(h * 0.055f)
            hint(c, if (MediaWatcher.access) "Start a song in YouTube Music, or any music app." else "On the phone: DriveSanity → Settings → Music.", tx, top + art * 0.62f, tw)
            return
        }
        number(h * 0.115f, Colors.FG)
        c.drawText(fit(np.title, tw), tx, top + art * 0.43f, text)
        label(h * 0.075f, Colors.FG)
        c.drawText(fit(np.artist, tw), tx, top + art * 0.60f, text)
        if (np.album.isNotEmpty()) {
            label(h * 0.055f)
            c.drawText(fit(np.album, tw), tx, top + art * 0.72f, text)
        }

        // How far through the song: thin ticks, like the rest of the app.
        if (np.durationMs > 0) {
            val f = (np.positionNow().toFloat() / np.durationMs).coerceIn(0f, 1f)
            val n = 56
            val pitch = tw / n
            val barTop = artRect.bottom - h * 0.115f
            val barBottom = barTop + h * 0.04f
            for (i in 0 until n) {
                val part = (f * n - i).coerceIn(0f, 1f)
                fill.color = if (part >= 0.98f) accent else if (part > 0.02f) Colors.mix(Colors.GRID, accent, part) else Colors.GRID
                val x = tx + i * pitch
                c.drawRect(x + pitch * 0.2f, barTop, x + pitch * 0.8f, barBottom, fill)
            }
            label(h * 0.048f)
            c.drawText(clock(np.positionNow()), tx, artRect.bottom, text)
            label(h * 0.048f, Colors.DIM, Paint.Align.RIGHT)
            c.drawText(clock(np.durationMs), tx + tw, artRect.bottom, text)
        }
    }

    private fun glyph(c: Canvas, g: Path, b: RectF, color: Int) {
        fill.color = color
        c.save()
        c.translate(b.left, b.top)
        c.scale(b.width() / 24f, b.height() / 24f)
        c.drawPath(g, fill)
        c.restore()
    }

    private fun fit(s: String, width: Float): String = TextUtils.ellipsize(s, TextPaint(text), width, TextUtils.TruncateAt.END).toString()

    private fun hint(c: Canvas, s: String, x: Float, y: Float, width: Float) {
        var line = ""
        var yy = y
        for (word in s.split(' ')) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (text.measureText(t) > width && line.isNotEmpty()) {
                c.drawText(line, x, yy, text)
                yy += text.textSize * 1.4f
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
