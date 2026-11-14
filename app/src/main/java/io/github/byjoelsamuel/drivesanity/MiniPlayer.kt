package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import io.github.byjoelsamuel.drivesanity.media.NowPlaying

/** Things on the map screen that can be pressed. */
enum class MapTouch { SEARCH, PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * A small now-playing card for over the map: the cover, the song and artist, a thin line for how far
 * through it is, and previous, play or pause and next. Only drawn while something is playing. It adds
 * what it draws to [hits] so a touch can be matched to a button.
 */
class MiniPlayer(context: Context) {
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val glyph = Path()

    fun draw(c: Canvas, r: RectF, np: NowPlaying, hits: MutableList<Pair<RectF, MapTouch>>) {
        val h = r.height()
        val radius = h * 0.26f
        val accent = np.accent
        // The card: dark and a little see-through, so the map shows faintly behind it.
        fill.color = Color.argb(225, 16, 18, 20)
        c.drawRoundRect(r, radius, radius, fill)

        val pad = h * 0.14f
        val cover = RectF(r.left + pad, r.top + pad, r.left + pad + (h - 2 * pad), r.bottom - pad)
        if (np.art != null) {
            c.save()
            clip.reset()
            clip.addRoundRect(cover, radius * 0.5f, radius * 0.5f, Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(np.art, null, cover, bmp)
            c.restore()
        } else {
            fill.color = Colors.BOX
            c.drawRoundRect(cover, radius * 0.5f, radius * 0.5f, fill)
        }

        // The three buttons at the right.
        val b = h * 0.3f
        val cy = r.centerY()
        val nextX = r.right - pad - b * 0.75f
        val playX = nextX - b * 2.3f
        val prevX = playX - b * 2.3f
        skip(c, prevX, cy, b * 0.55f, -1f)
        skip(c, nextX, cy, b * 0.55f, 1f)
        fill.color = Colors.mix(accent, Color.WHITE, 0.35f)
        c.drawCircle(playX, cy, b * 0.95f, fill)
        fill.color = Color.rgb(14, 16, 18)
        if (np.playing) {
            val bw = b * 0.2f
            val bh = b * 0.62f
            c.drawRoundRect(RectF(playX - b * 0.28f - bw / 2, cy - bh / 2, playX - b * 0.28f + bw / 2, cy + bh / 2), bw * 0.3f, bw * 0.3f, fill)
            c.drawRoundRect(RectF(playX + b * 0.28f - bw / 2, cy - bh / 2, playX + b * 0.28f + bw / 2, cy + bh / 2), bw * 0.3f, bw * 0.3f, fill)
        } else {
            glyph.reset()
            glyph.moveTo(playX - b * 0.25f, cy - b * 0.4f)
            glyph.lineTo(playX + b * 0.45f, cy)
            glyph.lineTo(playX - b * 0.25f, cy + b * 0.4f)
            glyph.close()
            c.drawPath(glyph, fill)
        }
        hits += RectF(prevX - b * 1.05f, r.top, prevX + b * 1.05f, r.bottom) to MapTouch.PREVIOUS
        hits += RectF(playX - b * 1.05f, r.top, playX + b * 1.05f, r.bottom) to MapTouch.PLAY_PAUSE
        hits += RectF(nextX - b * 1.05f, r.top, nextX + b * 1.05f, r.bottom) to MapTouch.NEXT

        // The song and the artist between the cover and the buttons.
        val tx = cover.right + pad * 1.2f
        val tw = prevX - b * 1.2f - tx
        if (tw > h) {
            text.typeface = numbers; text.letterSpacing = 0f; text.textSize = h * 0.3f; text.color = Colors.FG; text.textAlign = Paint.Align.LEFT
            c.drawText(fit(np.title, tw), tx, r.centerY() - h * 0.02f, text)
            text.typeface = labels; text.letterSpacing = 0.03f; text.textSize = h * 0.22f; text.color = Color.argb(205, 225, 225, 225)
            c.drawText(fit(np.artist, tw), tx, r.centerY() + h * 0.24f, text)
        }

        // How far through, as a thin line along the bottom edge of the card.
        if (np.durationMs > 0) {
            val f = (np.positionNow().toFloat() / np.durationMs).coerceIn(0f, 1f)
            val y = r.bottom - h * 0.05f
            val x0 = r.left + radius * 0.8f
            val x1 = r.right - radius * 0.8f
            fill.color = Color.argb(70, 255, 255, 255)
            c.drawRoundRect(RectF(x0, y - h * 0.018f, x1, y + h * 0.018f), h * 0.02f, h * 0.02f, fill)
            fill.color = accent
            c.drawRoundRect(RectF(x0, y - h * 0.018f, x0 + (x1 - x0) * f, y + h * 0.018f), h * 0.02f, h * 0.02f, fill)
        }
    }

    /** Previous (dir = -1) or next (dir = 1): a triangle and a bar on the side it points to. */
    private fun skip(c: Canvas, cx: Float, cy: Float, s: Float, dir: Float) {
        fill.color = Colors.FG
        glyph.reset()
        glyph.moveTo(cx - dir * s * 0.35f, cy - s * 0.55f)
        glyph.lineTo(cx + dir * s * 0.65f, cy)
        glyph.lineTo(cx - dir * s * 0.35f, cy + s * 0.55f)
        glyph.close()
        c.drawPath(glyph, fill)
        val bx = cx + dir * s * 0.95f
        c.drawRoundRect(RectF(bx - s * 0.09f, cy - s * 0.55f, bx + s * 0.09f, cy + s * 0.55f), s * 0.06f, s * 0.06f, fill)
    }

    private fun fit(s: String, width: Float): String = TextUtils.ellipsize(s, TextPaint(text), width, TextUtils.TruncateAt.END).toString()
}

/** A round search button for the corner of the map: a magnifying glass. Adds itself to [hits]. */
fun drawSearchButton(c: Canvas, fill: Paint, stroke: Paint, cx: Float, cy: Float, radius: Float, hits: MutableList<Pair<RectF, MapTouch>>) {
    fill.color = Color.argb(225, 16, 18, 20)
    c.drawCircle(cx, cy, radius, fill)
    stroke.color = Colors.FG
    stroke.style = Paint.Style.STROKE
    stroke.strokeCap = Paint.Cap.ROUND
    stroke.strokeWidth = radius * 0.14f
    val lens = radius * 0.34f
    c.drawCircle(cx - radius * 0.07f, cy - radius * 0.07f, lens, stroke)
    c.drawLine(cx + radius * 0.17f, cy + radius * 0.17f, cx + radius * 0.46f, cy + radius * 0.46f, stroke)
    hits += RectF(cx - radius * 1.2f, cy - radius * 1.2f, cx + radius * 1.2f, cy + radius * 1.2f) to MapTouch.SEARCH
}
