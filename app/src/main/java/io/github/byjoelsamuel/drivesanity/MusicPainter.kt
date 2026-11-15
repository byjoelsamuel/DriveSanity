package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.PathParser
import io.github.byjoelsamuel.drivesanity.media.CoverLook
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import io.github.byjoelsamuel.drivesanity.media.NowPlaying
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** The three buttons drawn on the Music screen. */
enum class MusicControl { PREVIOUS, PLAY_PAUSE, NEXT }

/**
 * The Music screen. The cover's own colours fill the whole screen as a soft blur behind everything. In front
 * of that: the cover in a rounded square, the app's icon and name, the song and artist, the song's length drawn
 * as a waveform that fills in as it plays (the bars near the playhead move while it's playing), and the
 * previous, play or pause and next buttons, all part of the same picture.
 *
 * The buttons here are drawn, so they're always there; [hit] says which one a touch landed on. On a screen
 * that takes touches they work. Android Auto also puts its own copies of the three buttons down the right
 * edge when the knob is turned or the screen is touched (set up in DashScreen), and on a car that only has a
 * knob those are the ones that can be pressed, so a strip is left free for them at the right.
 */
class MusicPainter(context: Context) {
    private val app = context.applicationContext
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.35f) })
    }
    private val clip = Path()
    private val glyphPath = Path()
    private val noteGlyph = PathParser.createPathFromPathData("M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z") ?: Path()

    // The colours and blur worked out for the cover on screen.
    private var lookFor: Bitmap? = null
    private var lookAspect = 0f
    private var look: CoverLook.Look? = null

    /** False when Android Auto is showing its own buttons beside the picture: the drawn ones are left out and the rest spreads into the room. */
    var buttonsDrawn = true

    private val hits = ArrayList<Pair<RectF, MusicControl>>()

    /** Which button a touch at ([x], [y]) landed on, in the surface's own pixels, or null. */
    fun hit(x: Float, y: Float): MusicControl? = hits.firstOrNull { it.first.contains(x, y) }?.second

    fun draw(c: Canvas, r: RectF, @Suppress("UNUSED_PARAMETER") s: Snapshot, @Suppress("UNUSED_PARAMETER") units: Units) {
        MediaWatcher.poll(app)
        render(c, r, MediaWatcher.now, MediaWatcher.access, SystemClock.uptimeMillis())
    }

    internal fun render(c: Canvas, r: RectF, np: NowPlaying?, access: Boolean, clockMs: Long) {
        val h = r.height()
        val w = r.width()
        hits.clear()

        // The backdrop: the cover's colours, blurred, darkened enough to keep the words readable.
        val cover = np?.art?.let { lookOf(it, w / h) }
        fill.shader = null
        fill.color = Colors.BG
        c.drawRect(r, fill)
        if (cover != null) {
            c.drawBitmap(cover.backdrop, null, r, backdropPaint)
            fill.shader = LinearGradient(
                r.left, r.top, r.left, r.bottom,
                intArrayOf(Color.argb(120, 8, 10, 12), Color.argb(150, 8, 10, 12), Color.argb(205, 8, 10, 12)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(r, fill)
            fill.shader = null
        }
        val accent = cover?.palette?.get(0) ?: np?.accent ?: Colors.ACCENT

        // The room: the left edge, a strip kept free at the right for the car's own buttons, and the top.
        val left = r.left + w * 0.05f
        val rightEdge = r.right - w * 0.11f
        val art = min(h * 0.74f, (rightEdge - left) * 0.36f)
        val top = r.top + (h - art) / 2 + h * 0.02f
        val artRect = RectF(left, top, left + art, top + art)

        if (cover == null && np != null) {
            fill.shader = RadialGradient(
                artRect.centerX(), artRect.centerY(), h * 0.95f,
                intArrayOf((accent and 0x00FFFFFF) or (0x34 shl 24), 0x00000000), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(r, fill)
            fill.shader = null
        }

        // A soft shadow under the cover so it lifts off the colour.
        fill.setShadowLayer(art * 0.09f, 0f, art * 0.03f, Color.argb(150, 0, 0, 0))
        fill.color = Colors.BOX
        val radius = art * 0.13f
        c.drawRoundRect(artRect, radius, radius, fill)
        fill.clearShadowLayer()

        if (np?.art != null) {
            c.save()
            clip.reset()
            clip.addRoundRect(artRect, radius, radius, Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(np.art, null, artRect, bmpPaint)
            c.restore()
        } else {
            glyph(c, noteGlyph, RectF(artRect).apply { inset(art * 0.3f, art * 0.3f) }, Colors.DIM)
        }

        // The words.
        val tx = artRect.right + w * 0.045f
        val tw = rightEdge - tx
        val soft = Color.argb(215, 236, 236, 236)
        val icon = art * 0.105f
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
        label(h * 0.046f, soft)
        c.drawText((np?.appName ?: "NOW PLAYING").uppercase().take(26), tx + icon + w * 0.015f, top + icon * 0.7f, text)

        if (np == null) {
            number(h * 0.1f, Colors.FG)
            c.drawText(fit(if (access) "Nothing playing" else "Allow notification access", tw), tx, top + art * 0.46f, text)
            label(h * 0.055f, soft)
            hint(c, if (access) "Start a song in YouTube Music, or any music app." else "On the phone: DriveSanity → Settings → Music.", tx, top + art * 0.62f, tw)
            return
        }
        // Sized from the cover as well as the screen, so in a narrow space the lines don't run into each other.
        number(min(h * 0.105f, art * 0.15f), Colors.FG)
        // Without the buttons underneath, the words and the waveform sit lower and get more room.
        val low = if (buttonsDrawn) 0f else art * 0.07f
        marquee(c, np.title, tx, top + art * 0.32f + low, tw, clockMs)
        label(min(h * 0.066f, art * 0.095f), soft)
        marquee(c, if (np.album.isNotEmpty() && np.artist.isNotEmpty()) "${np.artist} · ${np.album}" else np.artist.ifEmpty { np.album }, tx, top + art * 0.46f + low, tw, clockMs)

        waves(c, np, tx, tw, top + art * 0.595f + low * 2.6f, art * (if (buttonsDrawn) 0.085f else 0.11f), h, accent, clockMs)
        if (!buttonsDrawn) return

        // The three buttons, centred under the words.
        val cy = top + art * 0.905f
        val mid = tx + tw / 2
        val gap = min(tw * 0.28f, art * 0.34f)
        val big = art * 0.095f
        control(c, MusicControl.PREVIOUS, mid - gap, cy, art * 0.07f)
        // Play or pause: a filled disc in the cover's colour with the symbol cut out of it.
        fill.color = Colors.mix(accent, Color.WHITE, 0.35f)
        c.drawCircle(mid, cy, big, fill)
        fill.color = Color.rgb(14, 16, 18)
        if (np.playing) {
            val bw = big * 0.2f
            val bh = big * 0.62f
            c.drawRoundRect(RectF(mid - big * 0.3f - bw / 2, cy - bh / 2, mid - big * 0.3f + bw / 2, cy + bh / 2), bw * 0.3f, bw * 0.3f, fill)
            c.drawRoundRect(RectF(mid + big * 0.3f - bw / 2, cy - bh / 2, mid + big * 0.3f + bw / 2, cy + bh / 2), bw * 0.3f, bw * 0.3f, fill)
        } else {
            glyphPath.reset()
            glyphPath.moveTo(mid - big * 0.26f, cy - big * 0.4f)
            glyphPath.lineTo(mid + big * 0.46f, cy)
            glyphPath.lineTo(mid - big * 0.26f, cy + big * 0.4f)
            glyphPath.close()
            c.drawPath(glyphPath, fill)
        }
        hits += RectF(mid - big * 1.2f, cy - big * 1.2f, mid + big * 1.2f, cy + big * 1.2f) to MusicControl.PLAY_PAUSE
        control(c, MusicControl.NEXT, mid + gap, cy, art * 0.07f)
    }

    /**
     * The song's length as a row of bars with a gently rolling shape that's the same for the same song. The
     * played part is in the cover's colour, the rest pale; with the song playing, the bars around the
     * playhead rise and fall.
     */
    private fun waves(c: Canvas, np: NowPlaying, x: Float, width: Float, cy: Float, maxHalf: Float, h: Float, accent: Int, clockMs: Long) {
        val n = (width / (h * 0.019f)).toInt().coerceIn(28, 96)
        val pitch = width / n
        val seed = np.title.hashCode() xor np.artist.hashCode()
        val p1 = ((seed ushr 2) and 255) / 40f
        val p2 = ((seed ushr 9) and 255) / 33f
        val p3 = ((seed ushr 16) and 255) / 27f
        val f = if (np.durationMs > 0) (np.positionNow().toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
        val head = f * n
        stroke.strokeWidth = pitch * 0.5f
        for (i in 0 until n) {
            var v = 0.52f + 0.26f * sin(i * 0.31f + p1) + 0.17f * sin(i * 0.83f + p2) + 0.09f * sin(i * 1.9f + p3)
            v = v.coerceIn(0.2f, 1f)
            val edge = min(i, n - 1 - i) / 4f
            v *= 0.55f + 0.45f * min(1f, edge)
            if (np.playing) {
                val d = abs(i + 0.5f - head)
                if (d < 7f) v *= 1f + 0.22f * (1f - d / 7f) * sin(clockMs * 0.011f + i * 0.8f)
            }
            val half = maxHalf * v.coerceIn(0.12f, 1.2f)
            val px = x + (i + 0.5f) * pitch
            stroke.color = if (i + 0.5f <= head) accent else Color.argb(80, 255, 255, 255)
            c.drawLine(px, cy - half, px, cy + half, stroke)
        }
        if (np.durationMs > 0) {
            // The playhead, a little taller than the bars around it.
            stroke.color = Colors.FG
            stroke.strokeWidth = pitch * 0.62f
            val px = x + f * width
            c.drawLine(px, cy - maxHalf * 1.3f, px, cy + maxHalf * 1.3f, stroke)
            label(h * 0.046f, Color.argb(215, 236, 236, 236))
            c.drawText(clock(np.positionNow()), x, cy + maxHalf * 1.3f + h * 0.055f, text)
            label(h * 0.046f, Color.argb(215, 236, 236, 236), Paint.Align.RIGHT)
            c.drawText(clock(np.durationMs), x + width, cy + maxHalf * 1.3f + h * 0.055f, text)
        }
    }

    /** A previous or next button: a triangle and a bar, drawn in the pale text colour. */
    private fun control(c: Canvas, which: MusicControl, cx: Float, cy: Float, size: Float) {
        val dir = if (which == MusicControl.PREVIOUS) -1f else 1f
        fill.color = Colors.FG
        glyphPath.reset()
        glyphPath.moveTo(cx - dir * size * 0.35f, cy - size * 0.55f)
        glyphPath.lineTo(cx + dir * size * 0.65f, cy)
        glyphPath.lineTo(cx - dir * size * 0.35f, cy + size * 0.55f)
        glyphPath.close()
        c.drawPath(glyphPath, fill)
        // The bar goes on the side the triangle points to: |< for previous, >| for next.
        val barX = cx + dir * size * 0.95f
        c.drawRoundRect(RectF(barX - size * 0.09f, cy - size * 0.55f, barX + size * 0.09f, cy + size * 0.55f), size * 0.06f, size * 0.06f, fill)
        hits += RectF(cx - size * 1.45f, cy - size * 1.45f, cx + size * 1.45f, cy + size * 1.45f) to which
    }

    /** The player as a tall card, for the right-hand side of the map screen: cover, song, artist, waveform and buttons. */
    fun drawPanel(c: Canvas, r: RectF) {
        MediaWatcher.poll(app)
        renderPanel(c, r, MediaWatcher.now, MediaWatcher.access, SystemClock.uptimeMillis())
    }

    internal fun renderPanel(c: Canvas, r: RectF, np: NowPlaying?, access: Boolean, clockMs: Long) {
        hits.clear()
        val w = r.width()
        val h = r.height()
        val radius = min(w, h) * 0.07f
        c.save()
        clip.reset()
        clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.clipPath(clip)
        fill.shader = null
        fill.color = Color.rgb(16, 18, 20)
        c.drawRect(r, fill)
        val cover = np?.art?.let { lookOf(it, w / h) }
        if (cover != null) {
            c.drawBitmap(cover.backdrop, null, r, backdropPaint)
            fill.shader = LinearGradient(
                r.left, r.top, r.left, r.bottom,
                intArrayOf(Color.argb(110, 8, 10, 12), Color.argb(150, 8, 10, 12), Color.argb(210, 8, 10, 12)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(r, fill)
            fill.shader = null
        }
        val accent = cover?.palette?.get(0) ?: np?.accent ?: Colors.ACCENT
        val soft = Color.argb(215, 236, 236, 236)
        val p = w * 0.07f
        val inner = w - 2 * p

        if (np == null) {
            val g = min(inner, h * 0.3f)
            glyph(c, noteGlyph, RectF(r.centerX() - g * 0.25f, r.top + h * 0.25f, r.centerX() + g * 0.25f, r.top + h * 0.25f + g * 0.5f), Colors.DIM)
            number(min(w * 0.075f, h * 0.05f), Colors.FG, Paint.Align.CENTER)
            c.drawText(fit(if (access) "Nothing playing" else "Allow notification access", inner), r.centerX(), r.top + h * 0.56f, text)
            label(min(w * 0.05f, h * 0.034f), soft, Paint.Align.CENTER)
            hint(c, if (access) "Start a song in any music app." else "On the phone: Settings → Music.", r.left + p, r.top + h * 0.62f, inner, centred = true)
            c.restore()
            return
        }

        // The cover, centred at the top.
        val art = min(inner, h * 0.4f)
        val artRect = RectF(r.centerX() - art / 2, r.top + p, r.centerX() + art / 2, r.top + p + art)
        fill.setShadowLayer(art * 0.08f, 0f, art * 0.03f, Color.argb(150, 0, 0, 0))
        fill.color = Colors.BOX
        val cr = art * 0.12f
        c.drawRoundRect(artRect, cr, cr, fill)
        fill.clearShadowLayer()
        if (np.art != null) {
            c.save()
            clip.reset()
            clip.addRoundRect(artRect, cr, cr, Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(np.art, null, artRect, bmpPaint)
            c.restore()
        } else {
            glyph(c, noteGlyph, RectF(artRect).apply { inset(art * 0.3f, art * 0.3f) }, Colors.DIM)
        }

        // Song and artist, left-aligned to the card; long ones scroll.
        val tx = r.left + p
        number(min(w * 0.082f, h * 0.05f), Colors.FG)
        marquee(c, np.title, tx, artRect.bottom + h * 0.085f, inner, clockMs)
        label(min(w * 0.058f, h * 0.036f), soft)
        marquee(c, if (np.album.isNotEmpty() && np.artist.isNotEmpty()) "${np.artist} · ${np.album}" else np.artist.ifEmpty { np.album }, tx, artRect.bottom + h * 0.085f + h * 0.058f, inner, clockMs)

        val wavesY = artRect.bottom + h * 0.085f + h * 0.058f + h * (if (buttonsDrawn) 0.085f else 0.13f)
        waves(c, np, tx, inner, wavesY, h * (if (buttonsDrawn) 0.032f else 0.045f), h, accent, clockMs)
        if (!buttonsDrawn) {
            c.restore()
            return
        }

        // Buttons along the bottom.
        val big = min(w * 0.11f, h * 0.065f)
        val cy = r.bottom - p - big
        val gap = min(w * 0.27f, big * 3.4f)
        control(c, MusicControl.PREVIOUS, r.centerX() - gap, cy, big * 0.72f)
        fill.color = Colors.mix(accent, Color.WHITE, 0.35f)
        c.drawCircle(r.centerX(), cy, big, fill)
        fill.color = Color.rgb(14, 16, 18)
        if (np.playing) {
            val bw = big * 0.2f
            val bh = big * 0.62f
            c.drawRoundRect(RectF(r.centerX() - big * 0.3f - bw / 2, cy - bh / 2, r.centerX() - big * 0.3f + bw / 2, cy + bh / 2), bw * 0.3f, bw * 0.3f, fill)
            c.drawRoundRect(RectF(r.centerX() + big * 0.3f - bw / 2, cy - bh / 2, r.centerX() + big * 0.3f + bw / 2, cy + bh / 2), bw * 0.3f, bw * 0.3f, fill)
        } else {
            glyphPath.reset()
            glyphPath.moveTo(r.centerX() - big * 0.26f, cy - big * 0.4f)
            glyphPath.lineTo(r.centerX() + big * 0.46f, cy)
            glyphPath.lineTo(r.centerX() - big * 0.26f, cy + big * 0.4f)
            glyphPath.close()
            c.drawPath(glyphPath, fill)
        }
        hits += RectF(r.centerX() - big * 1.2f, cy - big * 1.2f, r.centerX() + big * 1.2f, cy + big * 1.2f) to MusicControl.PLAY_PAUSE
        control(c, MusicControl.NEXT, r.centerX() + gap, cy, big * 0.72f)
        c.restore()
    }

    // When each long line was first drawn, so its scrolling starts from the beginning for a new song.
    private val marqueeSeen = HashMap<String, Long>()

    /**
     * Draws [s] on one line. If it's too long for [width] it slowly slides sideways to show all of it,
     * waits at the end, then starts again, so the whole name can be read.
     */
    private fun marquee(c: Canvas, s: String, x: Float, y: Float, width: Float, clockMs: Long) {
        val tw = text.measureText(s)
        if (tw <= width) {
            c.drawText(s, x, y, text)
            return
        }
        if (marqueeSeen.size > 24) marqueeSeen.clear()
        val start = marqueeSeen.getOrPut(s) { clockMs }
        val travel = tw - width + text.textSize * 0.6f
        val speed = text.textSize * 3.2f // pixels a second
        val hold = 1800L
        val scrollMs = (travel / speed * 1000).toLong()
        val t = (clockMs - start) % (hold + scrollMs + hold)
        val offset = when {
            t < hold -> 0f
            t < hold + scrollMs -> travel * (t - hold) / scrollMs
            else -> travel
        }
        c.save()
        c.clipRect(x, y - text.textSize * 1.1f, x + width, y + text.textSize * 0.4f)
        c.drawText(s, x - offset, y, text)
        c.restore()
    }

    private fun lookOf(art: Bitmap, aspect: Float): CoverLook.Look? {
        if (art !== lookFor || abs(aspect - lookAspect) > 0.05f) {
            lookFor = art
            lookAspect = aspect
            look = CoverLook.of(art, aspect)
        }
        return look
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

    private fun hint(c: Canvas, s: String, x: Float, y: Float, width: Float, centred: Boolean = false) {
        var line = ""
        var yy = y
        for (word in s.split(' ')) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (text.measureText(t) > width && line.isNotEmpty()) {
                c.drawText(line, if (centred) x + width / 2 else x, yy, text)
                yy += text.textSize * 1.4f
                line = word
            } else line = t
        }
        if (line.isNotEmpty()) c.drawText(line, if (centred) x + width / 2 else x, yy, text)
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
