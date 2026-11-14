package io.github.byjoelsamuel.drivesanity.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What the Music screen takes from the album cover: a few of its colours, and a heavily blurred copy of
 * it to use as the backdrop. Worked out once per cover, not every frame.
 */
object CoverLook {
    /** [palette] is up to three colours, the strongest first, each lifted to read on a dark screen. */
    class Look(val palette: IntArray, val backdrop: Bitmap)

    private const val W = 64

    /** The cover's colours and a blurred backdrop shaped like a screen of [aspect] (width over height). Null if the picture can't be read. */
    fun of(art: Bitmap, aspect: Float): Look? = try {
        val soft = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) ?: return null else art
        val h = max(8, (W / aspect.coerceIn(0.5f, 4f)).toInt())
        // Crop the cover to the screen's shape, shrink it to a few dozen pixels, then smear it.
        val src = Rect(0, 0, soft.width, soft.height)
        val wanted = W.toFloat() / h
        if (soft.width.toFloat() / soft.height > wanted) {
            val cw = (soft.height * wanted).toInt()
            src.set((soft.width - cw) / 2, 0, (soft.width - cw) / 2 + cw, soft.height)
        } else {
            val ch = (soft.width / wanted).toInt()
            src.set(0, (soft.height - ch) / 2, soft.width, (soft.height - ch) / 2 + ch)
        }
        val small = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(soft, src, Rect(0, 0, W, h), Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(W * h)
        small.getPixels(px, 0, W, 0, 0, W, h)
        val colours = paletteOf(px)
        repeat(3) { blur(px, W, h, 4) }
        small.setPixels(px, 0, W, 0, 0, W, h)
        Look(colours, small)
    } catch (_: Exception) {
        null
    }

    /** Box blur over [px], rows then columns. Three passes of it come close to a gaussian. */
    internal fun blur(px: IntArray, w: Int, h: Int, radius: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, len: Int, count: Int, stride: Int, step: Int) {
            for (line in 0 until count) {
                val base = line * stride
                for (i in 0 until len) {
                    var a = 0; var r = 0; var g = 0; var b = 0; var n = 0
                    for (k in max(0, i - radius)..min(len - 1, i + radius)) {
                        val p = src[base + k * step]
                        a += p ushr 24; r += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; n++
                    }
                    dst[base + i * step] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        pass(px, tmp, w, h, w, 1)
        pass(tmp, px, h, w, 1, w)
    }

    /**
     * The strongest colours in the picture: pixels are grouped by hue, each counted by how vivid and bright
     * it is, so a big dull background doesn't beat a small bright subject. The second and third have to sit
     * well apart in hue. A grey cover gives a cool grey-blue.
     */
    internal fun paletteOf(px: IntArray): IntArray {
        val weight = FloatArray(12)
        val sumR = FloatArray(12); val sumG = FloatArray(12); val sumB = FloatArray(12)
        val hsv = FloatArray(3)
        var total = 0f
        for (p in px) {
            Color.colorToHSV(p, hsv)
            val w = hsv[1] * hsv[2] * if (hsv[2] < 0.15f) 0.1f else 1f
            if (w < 0.04f) continue
            val bin = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            weight[bin] += w
            sumR[bin] += w * Color.red(p); sumG[bin] += w * Color.green(p); sumB[bin] += w * Color.blue(p)
            total += w
        }
        if (total < px.size * 0.02f) return intArrayOf(GREY, GREY, GREY)
        val order = (0 until 12).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
        val picked = ArrayList<Int>()
        for (bin in order) {
            if (picked.size == 3) break
            if (picked.all { hueGap(it, bin) >= 3 }) picked += bin
        }
        // Fewer than three distinct hues: lean on the strongest one's lighter and darker sides.
        val colours = picked.map { bin -> lift(Color.rgb((sumR[bin] / weight[bin]).toInt(), (sumG[bin] / weight[bin]).toInt(), (sumB[bin] / weight[bin]).toInt())) }.toMutableList()
        while (colours.size < 3) colours += shade(colours.first(), if (colours.size == 1) 0.7f else 0.45f)
        return colours.toIntArray()
    }

    private val GREY = Color.rgb(143, 160, 178)

    private fun hueGap(a: Int, b: Int): Int {
        val d = abs(a - b)
        return min(d, 12 - d)
    }

    /** Brightens a colour enough to show on a dark background without turning it pastel. */
    private fun lift(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = hsv[1].coerceIn(0.35f, 0.9f)
        hsv[2] = hsv[2].coerceIn(0.72f, 0.96f)
        return Color.HSVToColor(hsv)
    }

    private fun shade(c: Int, v: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = (hsv[2] * v).coerceIn(0f, 1f)
        return Color.HSVToColor(hsv)
    }
}
