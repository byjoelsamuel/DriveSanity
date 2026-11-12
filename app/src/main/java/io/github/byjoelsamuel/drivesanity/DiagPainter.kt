package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import io.github.byjoelsamuel.drivesanity.obd.Dtc
import io.github.byjoelsamuel.drivesanity.obd.Obd
import io.github.byjoelsamuel.drivesanity.obd.Pid
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The OBD2 screen: the check-engine lamp and fault codes, live engine readings, and facts about
 * the car and the adapter. Meant for parked or slow moments, so it's text-heavy.
 */
class DiagPainter(context: Context) {
    private val numbers = Fonts.semibold(context)
    private val labels = Fonts.medium(context)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = numbers }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun label(size: Float, color: Int = Colors.DIM, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = labels
        text.letterSpacing = 0.06f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    private fun number(size: Float, color: Int = Colors.FG, align: Paint.Align = Paint.Align.LEFT) {
        text.typeface = numbers
        text.letterSpacing = 0f
        text.textSize = size
        text.color = color
        text.textAlign = align
    }

    fun draw(c: Canvas, r: RectF, s: Snapshot, units: Units) {
        fill.color = Colors.BG
        c.drawRect(r, fill)
        val pad = r.height() * 0.04f
        val inner = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        val h = inner.height()
        header(c, RectF(inner.left, inner.top, inner.right, inner.top + h * 0.08f))
        val body = RectF(inner.left, inner.top + h * 0.12f, inner.right, inner.bottom)
        val gap = pad
        if (r.width() > r.height() * 1.2f) {
            val w1 = body.width() * 0.38f
            val w2 = body.width() * 0.31f
            faults(c, RectF(body.left, body.top, body.left + w1 - gap, body.bottom))
            liveData(c, RectF(body.left + w1, body.top, body.left + w1 + w2 - gap, body.bottom), units)
            carInfo(c, RectF(body.left + w1 + w2, body.top, body.right, body.bottom), s, units)
        } else {
            val third = body.height() / 3f
            faults(c, RectF(body.left, body.top, body.right, body.top + third - gap))
            liveData(c, RectF(body.left, body.top + third, body.right, body.top + 2 * third - gap), units)
            carInfo(c, RectF(body.left, body.top + 2 * third, body.right, body.bottom), s, units)
        }
    }

    private fun header(c: Canvas, b: RectF) {
        val h = b.height()
        number(h * 0.9f)
        c.drawText("OBD2", b.left, b.bottom - h * 0.1f, text)
        val live = Obd.status.link == Obd.Link.LIVE
        label(h * 0.5f, if (live) Colors.GOOD else Colors.SAND, Paint.Align.RIGHT)
        c.drawText(if (live) "● ${Obd.status.detail}" else Obd.describe(), b.right, b.bottom - h * 0.2f, text)
    }

    private fun title(c: Canvas, b: RectF, name: String): Float {
        val size = b.height() * 0.06f
        label(size)
        c.drawText(name, b.left, b.top + size, text)
        fill.color = Colors.GRID
        c.drawRect(b.left, b.top + size * 1.5f, b.right, b.top + size * 1.5f + max(2f, size * 0.1f), fill)
        return b.top + size * 2.2f
    }

    // ---- fault codes ----

    private fun faults(c: Canvas, b: RectF) {
        val top = title(c, b, "FAULT CODES")
        val rowH = b.height() * 0.14f
        val report = Obd.dtcReport
        if (Obd.status.link != Obd.Link.LIVE) {
            note(c, b, top, "Connect the adapter to read fault codes.")
            return
        }
        if (report == null) {
            note(c, b, top, "Reading…")
            return
        }
        // The check-engine lamp.
        val lampOn = report.milOn == true
        fill.color = when (report.milOn) { true -> Colors.RED; false -> Colors.GOOD; null -> Colors.DIM }
        c.drawCircle(b.left + rowH * 0.3f, top + rowH * 0.5f, rowH * 0.22f, fill)
        label(rowH * 0.34f, Colors.FG)
        c.drawText("CHECK ENGINE", b.left + rowH * 0.75f, top + rowH * 0.45f, text)
        number(rowH * 0.5f, if (lampOn) Colors.RED else Colors.GOOD, Paint.Align.RIGHT)
        c.drawText(when (report.milOn) { true -> "ON"; false -> "OFF"; null -> "–" }, b.right, top + rowH * 0.52f, text)

        val codes = report.all
        var y = top + rowH * 1.25f
        if (codes.isEmpty()) {
            number(rowH * 0.5f, Colors.GOOD)
            c.drawText("No fault codes", b.left, y + rowH * 0.45f, text)
        } else {
            val fit = max(1, ((b.bottom - y - rowH * 0.9f) / (rowH * 1.1f)).toInt())
            for (code in codes.take(fit)) {
                number(rowH * 0.5f, Colors.FG)
                c.drawText(code, b.left, y + rowH * 0.45f, text)
                label(rowH * 0.3f)
                val x = b.left + rowH * 2.1f
                val desc = TextUtils.ellipsize(Dtc.describe(code), text, b.right - x, TextUtils.TruncateAt.END)
                c.drawText(desc, 0, desc.length, x, y + rowH * 0.42f, text)
                y += rowH * 1.1f
            }
            if (codes.size > fit) {
                label(rowH * 0.3f)
                c.drawText("+${codes.size - fit} more", b.left, y + rowH * 0.4f, text)
            }
        }
        label(rowH * 0.26f)
        val ago = ((System.currentTimeMillis() - report.readAtMs) / 1000).coerceAtLeast(0)
        c.drawText(
            "Stored ${report.stored.size} · Pending ${report.pending.size} · Permanent ${report.permanent.size} · read ${if (ago < 90) "${ago}s" else "${ago / 60}m"} ago",
            b.left, b.bottom - rowH * 0.1f, text,
        )
    }

    private fun note(c: Canvas, b: RectF, top: Float, msg: String) {
        label(b.height() * 0.07f)
        c.drawText(msg, b.left, top + b.height() * 0.1f, text)
    }

    // ---- live data ----

    private fun liveData(c: Canvas, b: RectF, units: Units) {
        val top = title(c, b, "LIVE DATA")
        val rows = mutableListOf<Pair<String, String>>()
        fun add(name: String, pid: Pid, fmt: (Float) -> String) {
            Obd.extra(pid)?.let { rows += name to fmt(it) }
        }
        add("SHORT FUEL TRIM", Pid.TRIM_SHORT) { "%+.1f %%".format(it) }
        add("LONG FUEL TRIM", Pid.TRIM_LONG) { "%+.1f %%".format(it) }
        add("AIR FLOW", Pid.MAF) { "%.1f g/s".format(it) }
        add("MANIFOLD", Pid.MAP) { "%.0f kPa".format(it) }
        add("TIMING", Pid.TIMING) { "%.0f°".format(it) }
        add("AIR / FUEL", Pid.LAMBDA) { "%.1f : 1".format(it * 14.7f) }
        add("OUTSIDE", Pid.AMBIENT) { if (units == Units.MPH) "${(it * 9 / 5 + 32).roundToInt()}°F" else "${it.roundToInt()}°C" }
        add("ENGINE ON", Pid.RUNTIME) { "%d:%02d".format((it / 60).toInt(), (it % 60).toInt()) }
        if (rows.isEmpty()) {
            note(c, b, top, if (Obd.status.link == Obd.Link.LIVE) "Waiting for readings…" else "Not connected.")
            return
        }
        val rowH = min((b.bottom - top) / 8f, b.height() * 0.115f)
        rows.forEachIndexed { i, (name, value) -> kv(c, b, top + i * rowH, rowH, name, value) }
    }

    // ---- car and adapter ----

    private fun carInfo(c: Canvas, b: RectF, s: Snapshot, units: Units) {
        val top = title(c, b, "CAR")
        val rowH = b.height() * 0.115f
        val live = Obd.status.link == Obd.Link.LIVE
        var i = 0
        fun row(name: String, value: String) = kv(c, b, top + i++ * rowH, rowH, name, value)
        row("PROTOCOL", Obd.protocol.ifEmpty { "–" })
        row("BATTERY", s.volts?.let { "%.1f V".format(it) } ?: "–")
        row("VIN", Obd.vin ?: "–")
        row("READINGS", if (live) "${Obd.supported.size} supported" else "–")
        row("LINK RESTARTS", Obd.reconnects.toString())

        // Tires, as on the other screens.
        val ty = top + i * rowH + rowH * 0.2f
        label(rowH * 0.34f)
        c.drawText("TIRES ${Tpms.unit(units)}", b.left, ty + rowH * 0.4f, text)
        val cellW = b.width() / 4f
        for (k in 0 until 4) {
            val psi = s.tires.getOrNull(k)
            val color = when {
                psi == null -> Colors.DIM
                Tpms.level(psi) == 2 -> Colors.RED
                Tpms.level(psi) == 1 -> Colors.SAND
                else -> Colors.FG
            }
            val x = b.left + k * cellW + cellW / 2
            number(rowH * 0.62f, color, Paint.Align.CENTER)
            c.drawText(Tpms.text(psi, units), x, ty + rowH * 1.2f, text)
            label(rowH * 0.3f, Colors.DIM, Paint.Align.CENTER)
            c.drawText(Tpms.NAMES[k], x, ty + rowH * 1.62f, text)
        }
    }

    /** A small caption on the left, its value on the right. */
    private fun kv(c: Canvas, b: RectF, y: Float, rowH: Float, name: String, value: String) {
        label(rowH * 0.3f)
        c.drawText(name, b.left, y + rowH * 0.6f, text)
        number(rowH * 0.44f, if (value == "–") Colors.DIM else Colors.FG, Paint.Align.RIGHT)
        val room = b.width() - text.measureText("$name   ") * 1.1f
        val v = TextUtils.ellipsize(value, text, max(room, b.width() * 0.3f), TextUtils.TruncateAt.MIDDLE)
        c.drawText(v, 0, v.length, b.right, y + rowH * 0.62f, text)
    }
}
