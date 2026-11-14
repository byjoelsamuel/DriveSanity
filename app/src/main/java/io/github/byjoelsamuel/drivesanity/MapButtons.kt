package io.github.byjoelsamuel.drivesanity

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Things on the map screen that can be pressed. */
enum class MapTouch { SEARCH, PREVIOUS, PLAY_PAUSE, NEXT }

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
