package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import io.github.byjoelsamuel.drivesanity.mirror.MapMirror
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The "Google Maps" screen: the phone's Google Maps, copied across, as large as it fits. A phone is tall and a car
 * screen is wide, so there are bands left and right; the speed sits in the left one. With nothing coming through
 * it says how to start, in words big enough to read at a glance.
 */
class MirrorPainter(context: Context) {
    private val numbers = Fonts.dashNumbers(context)
    private val labels = Fonts.dashLabels(context)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units, full: RectF = r) {
        fill.color = Colors.BG
        c.drawRect(full, fill)
        val shown = MapMirror.draw(c, RectF(r), bitmapPaint)
        if (shown == null) {
            drawWaiting(c, r)
            return
        }
        // A thin edge so the copied screen reads as one panel.
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = max(1f, r.height() * 0.003f)
        fill.color = Colors.GRID
        c.drawRect(shown, fill)
        fill.style = Paint.Style.FILL
        val left = shown.left - r.left
        if (left > r.height() * 0.5f) drawSpeed(c, RectF(r.left, r.top, shown.left, r.bottom), s, units)
    }

    private fun drawSpeed(c: Canvas, band: RectF, s: Snapshot, units: Units) {
        val v = s.speedMps?.let { units.speed(it).roundToInt().coerceAtLeast(0).toString() } ?: "–"
        val size = minOf(band.width() * 0.42f, band.height() * 0.3f)
        text.typeface = numbers
        text.letterSpacing = 0f
        text.textAlign = Paint.Align.CENTER
        text.textSize = size
        text.color = Colors.FG
        c.drawText(v, band.centerX(), band.centerY() + size * 0.3f, text)
        text.typeface = labels
        text.letterSpacing = 0.06f
        text.textSize = size * 0.28f
        text.color = Colors.DIM
        c.drawText(units.label.uppercase(), band.centerX(), band.centerY() + size * 0.3f + size * 0.42f, text)
    }

    private fun drawWaiting(c: Canvas, r: RectF) {
        val sharing = MapMirror.state is MapMirror.State.Sharing
        val size = r.height() * 0.075f
        text.typeface = labels
        text.letterSpacing = 0.02f
        text.textAlign = Paint.Align.CENTER
        text.textSize = size
        text.color = Colors.FG
        val title = if (sharing) "Waiting for Google Maps…" else "Google Maps isn't being shared"
        c.drawText(title, r.centerX(), r.centerY() - size * 0.4f, text)
        text.textSize = size * 0.62f
        text.color = Colors.DIM
        val line = if (sharing) "Open Google Maps on the phone and keep it on the screen. Turn the phone sideways for a bigger picture." else "On the phone: Settings → Google Maps directions → Share Google Maps."
        c.drawText(line, r.centerX(), r.centerY() + size * 0.9f, text)
    }
}
