package io.github.byjoelsamuel.drivesanity.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import io.github.byjoelsamuel.drivesanity.Colors
import io.github.byjoelsamuel.drivesanity.Snapshot
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.nav.Arrow
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.Route
import io.github.byjoelsamuel.drivesanity.nav.SpeedLimit
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The map panel: OpenStreetMap tiles turned with the car (heading up), the route, the car
 * arrow, the next turn on top and the speed limit in the corner.
 */
class MapPainter(private val labels: Typeface, private val numbers: Typeface) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val path = Path()
    private val dst = RectF()

    private var cachedRoute: Route? = null
    private var cachedZoom = -1
    private var routeWorld = DoubleArray(0)

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        val corner = min(r.width(), r.height()) * 0.05f
        c.save()
        clip.reset()
        clip.addRoundRect(r, corner, corner, Path.Direction.CW)
        c.clipPath(clip)
        fill.color = MAP_BG
        c.drawRect(r, fill)

        val pos = s.position
        if (pos == null) {
            label(r.height() * 0.06f, Colors.DIM, Paint.Align.CENTER)
            c.drawText("Waiting for GPS…", r.centerX(), r.centerY(), text)
            c.restore()
            return
        }

        // Closer in when slow, further out on the highway.
        val kmh = (s.speedMps ?: 0f) * 3.6f
        val zoom = when {
            kmh < 35f -> 17
            kmh < 80f -> 16
            else -> 15
        }
        val k = (min(r.width(), r.height()) / 380f).coerceIn(1f, 2.6f)
        val carX = r.centerX()
        val carY = r.top + r.height() * 0.66f
        val wx = Geo.worldX(pos.lon, zoom)
        val wy = Geo.worldY(pos.lat, zoom)

        c.save()
        c.rotate(-(s.headingDeg ?: 0f), carX, carY)
        val reach = hypot(r.width(), r.height()) / k
        val t = Geo.TILE
        for (tx in floor((wx - reach) / t).toInt()..floor((wx + reach) / t).toInt()) {
            for (ty in floor((wy - reach) / t).toInt()..floor((wy + reach) / t).toInt()) {
                val bmp = Tiles.get(zoom, tx, ty) ?: continue
                val left = carX + ((tx * t - wx) * k).toFloat()
                val top = carY + ((ty * t - wy) * k).toFloat()
                dst.set(left, top, left + t * k, top + t * k)
                c.drawBitmap(bmp, null, dst, tilePaint)
            }
        }
        Nav.route?.let { drawRoute(c, it, zoom, wx, wy, carX, carY, k) }
        c.restore()

        drawCar(c, carX, carY, 11f * k)
        drawBanner(c, r, units)
        drawLimit(c, r, units)

        label(r.height() * 0.032f, Colors.DIM, Paint.Align.LEFT)
        c.drawText("© OpenStreetMap", r.left + corner * 0.6f, r.bottom - corner * 0.5f, text)
        c.restore()
    }

    private fun drawRoute(c: Canvas, route: Route, zoom: Int, wx: Double, wy: Double, cx: Float, cy: Float, k: Float) {
        if (route !== cachedRoute || zoom != cachedZoom) {
            routeWorld = DoubleArray(route.points.size * 2)
            route.points.forEachIndexed { i, p ->
                routeWorld[i * 2] = Geo.worldX(p.lon, zoom)
                routeWorld[i * 2 + 1] = Geo.worldY(p.lat, zoom)
            }
            cachedRoute = route
            cachedZoom = zoom
        }
        path.reset()
        for (i in 0 until routeWorld.size / 2) {
            val x = cx + ((routeWorld[i * 2] - wx) * k).toFloat()
            val y = cy + ((routeWorld[i * 2 + 1] - wy) * k).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        stroke.color = Colors.BG
        stroke.strokeWidth = 9f * k
        c.drawPath(path, stroke)
        stroke.color = Colors.ACCENT
        stroke.strokeWidth = 6f * k
        c.drawPath(path, stroke)
    }

    private fun drawCar(c: Canvas, x: Float, y: Float, size: Float) {
        path.reset()
        path.moveTo(x, y - size)
        path.lineTo(x + size * 0.75f, y + size * 0.8f)
        path.lineTo(x, y + size * 0.4f)
        path.lineTo(x - size * 0.75f, y + size * 0.8f)
        path.close()
        fill.color = Colors.FG
        c.drawPath(path, fill)
        stroke.color = Colors.BG
        stroke.strokeWidth = size * 0.18f
        c.drawPath(path, stroke)
    }

    /** Next turn, how far, and what's left; or a short message ("Finding a route…"). */
    private fun drawBanner(c: Canvas, r: RectF, units: Units) {
        val route = Nav.route
        val msg = Nav.message
        if (route == null && msg == null) return
        val m = r.width() * 0.04f
        val h = r.height() * (if (route != null) 0.22f else 0.12f)
        val b = RectF(r.left + m, r.top + m, r.right - m, r.top + m + h)
        fill.color = Colors.BOX
        fill.alpha = 240
        c.drawRoundRect(b, h * 0.18f, h * 0.18f, fill)
        fill.alpha = 255

        if (route == null || route.steps.isEmpty()) {
            label(h * 0.38f, Colors.FG, Paint.Align.LEFT)
            val line = TextUtils.ellipsize(msg ?: "", text, b.width() - h * 0.5f, TextUtils.TruncateAt.END)
            c.drawText(line, 0, line.length, b.left + h * 0.25f, b.centerY() + h * 0.13f, text)
            return
        }
        val step = route.steps[Nav.next]
        val icon = h * 0.62f
        drawArrow(c, step.arrow, b.left + h * 0.2f + icon / 2, b.centerY(), icon)
        val tx = b.left + h * 0.3f + icon
        value(h * 0.3f, Colors.FG, Paint.Align.LEFT)
        c.drawText(distance(Nav.toNextM, units), tx, b.top + h * 0.38f, text)
        label(h * 0.2f, Colors.FG, Paint.Align.LEFT)
        val what = TextUtils.ellipsize(step.text, text, b.right - tx - h * 0.15f, TextUtils.TruncateAt.END)
        c.drawText(what, 0, what.length, tx, b.top + h * 0.63f, text)
        label(h * 0.17f, Colors.DIM, Paint.Align.LEFT)
        val left = "${distance(Nav.remainingM, units)} · ${(Nav.remainingS / 60).roundToInt()} min · ETA ${Nav.arrivalTime()}"
        c.drawText(left, tx, b.top + h * 0.86f, text)
    }

    private fun drawArrow(c: Canvas, a: Arrow, cx: Float, cy: Float, size: Float) {
        val s = size * 0.4f
        stroke.color = Colors.FG
        stroke.strokeWidth = size * 0.12f
        path.reset()
        when (a) {
            Arrow.ARRIVE -> {
                fill.color = Colors.FG
                c.drawCircle(cx, cy - s * 0.2f, s * 0.5f, fill)
                c.drawLine(cx, cy + s * 0.3f, cx, cy + s, stroke)
                return
            }
            Arrow.STRAIGHT -> {
                path.moveTo(cx, cy + s); path.lineTo(cx, cy - s)
                head(cx, cy - s, 0f, -1f, s)
            }
            Arrow.LEFT, Arrow.RIGHT -> {
                val d = if (a == Arrow.LEFT) -1f else 1f
                path.moveTo(cx - d * s * 0.4f, cy + s); path.lineTo(cx - d * s * 0.4f, cy - s * 0.2f); path.lineTo(cx + d * s, cy - s * 0.2f)
                head(cx + d * s, cy - s * 0.2f, d, 0f, s)
            }
            Arrow.SLIGHT_LEFT, Arrow.SLIGHT_RIGHT -> {
                val d = if (a == Arrow.SLIGHT_LEFT) -1f else 1f
                path.moveTo(cx - d * s * 0.3f, cy + s); path.lineTo(cx - d * s * 0.3f, cy); path.lineTo(cx + d * s * 0.6f, cy - s * 0.9f)
                head(cx + d * s * 0.6f, cy - s * 0.9f, d * 0.7f, -0.7f, s)
            }
            Arrow.UTURN -> {
                path.moveTo(cx + s * 0.5f, cy + s); path.lineTo(cx + s * 0.5f, cy - s * 0.3f)
                path.quadTo(cx + s * 0.5f, cy - s, cx, cy - s); path.quadTo(cx - s * 0.5f, cy - s, cx - s * 0.5f, cy - s * 0.3f)
                path.lineTo(cx - s * 0.5f, cy + s * 0.4f)
                head(cx - s * 0.5f, cy + s * 0.4f, 0f, 1f, s)
            }
        }
        c.drawPath(path, stroke)
    }

    /** Adds an arrowhead at (x, y) pointing along (dx, dy) to [path]. */
    private fun head(x: Float, y: Float, dx: Float, dy: Float, s: Float) {
        val l = s * 0.5f
        // Two strokes at ±45° back from the tip.
        val bx = -dx; val by = -dy
        path.moveTo(x + (bx - by) * l * 0.7f, y + (by + bx) * l * 0.7f)
        path.lineTo(x, y)
        path.lineTo(x + (bx + by) * l * 0.7f, y + (by - bx) * l * 0.7f)
    }

    /** A US-style rectangle with MPH, a round European-style sign with km/h. */
    private fun drawLimit(c: Canvas, r: RectF, units: Units) {
        val limit = SpeedLimit.current ?: return
        val size = r.height() * 0.2f
        val m = r.width() * 0.04f
        val b = RectF(r.right - m - size * 0.8f, r.bottom - m - size, r.right - m, r.bottom - m)
        if (units == Units.MPH) {
            fill.color = Color.WHITE
            c.drawRoundRect(b, size * 0.1f, size * 0.1f, fill)
            stroke.color = Color.BLACK
            stroke.strokeWidth = size * 0.04f
            val inset = size * 0.06f
            c.drawRoundRect(RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset), size * 0.07f, size * 0.07f, stroke)
            label(size * 0.14f, Color.BLACK, Paint.Align.CENTER)
            text.letterSpacing = 0f
            c.drawText("SPEED", b.centerX(), b.top + size * 0.3f, text)
            c.drawText("LIMIT", b.centerX(), b.top + size * 0.45f, text)
            value(size * 0.42f, Color.BLACK, Paint.Align.CENTER)
            c.drawText(limit.inMph().toString(), b.centerX(), b.bottom - size * 0.12f, text)
        } else {
            val cx = b.centerX()
            val cy = b.centerY()
            val rad = min(b.width(), b.height()) / 2f
            fill.color = Color.WHITE
            c.drawCircle(cx, cy, rad, fill)
            stroke.color = Colors.RED
            stroke.strokeWidth = rad * 0.22f
            c.drawCircle(cx, cy, rad * 0.86f, stroke)
            value(rad * 0.8f, Color.BLACK, Paint.Align.CENTER)
            c.drawText(limit.inKmh().toString(), cx, cy + rad * 0.29f, text)
        }
    }

    private fun label(size: Float, color: Int, align: Paint.Align) {
        text.typeface = labels
        text.letterSpacing = 0.04f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    private fun value(size: Float, color: Int, align: Paint.Align) {
        text.typeface = numbers
        text.letterSpacing = 0f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    companion object {
        private val MAP_BG = Color.rgb(22, 24, 27)

        fun distance(m: Double, units: Units): String = if (units == Units.MPH) {
            val ft = m * 3.28084
            if (ft < 1000) "${(ft / 50).roundToInt() * 50} ft" else "%.1f mi".format(m / 1609.344)
        } else {
            if (m < 1000) "${(m / 10).roundToInt() * 10} m" else "%.1f km".format(m / 1000)
        }
    }
}
