package io.github.byjoelsamuel.drivesanity.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.MusicPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MusicPainterTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** A cover with a big dark background, a small bright orange sun and a teal band. */
    private fun cover(): Bitmap {
        val b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.rgb(24, 26, 34))
        p.color = Color.rgb(20, 150, 150)
        c.drawRect(0f, 200f, 300f, 300f, p)
        p.color = Color.rgb(255, 120, 30)
        c.drawCircle(150f, 110f, 60f, p)
        return b
    }

    private fun playing(art: Bitmap?, positionMs: Long = 95_000, isPlaying: Boolean = true) = NowPlaying(
        title = "Neon Overdrive", artist = "The Night Shift", album = "Midnight Run", art = art,
        accent = Color.rgb(143, 179, 194), appIcon = null, appName = "YouTube Music", playing = isPlaying,
        positionMs = positionMs, durationMs = 214_000, basedAtMs = SystemClock.elapsedRealtime(), speed = 1f,
        canPrev = true, canNext = true, pkg = "test",
    )

    private fun shot(name: String, w: Int, h: Int, np: NowPlaying?, access: Boolean = true): MusicPainter {
        val painter = MusicPainter(ctx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        painter.render(Canvas(bmp), RectF(0f, 0f, w.toFloat(), h.toFloat()), np, access, 1_000L)
        System.getenv("SHOTS_DIR")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return painter
    }

    @Test
    fun paletteFindsTheVividColoursNotTheBigDullOne() {
        val look = CoverLook.of(cover(), 2.5f)
        assertNotNull(look)
        look!!
        val first = FloatArray(3).also { Color.colorToHSV(look.palette[0], it) }
        // The sun (orange, hue about 25) is the strongest; the teal band (about 180) is among the others.
        assertTrue("first colour is the orange sun, was hue ${first[0]}", first[0] in 10f..45f)
        assertTrue(look.palette.drop(1).any { c -> FloatArray(3).also { Color.colorToHSV(c, it) }[0] in 160f..200f })
        assertEquals(3, look.palette.size)
    }

    @Test
    fun backdropIsBlurredToTheScreensShape() {
        val look = CoverLook.of(cover(), 2.0f)!!
        assertEquals(64, look.backdrop.width)
        assertEquals(32, look.backdrop.height)
        // A hard edge in the cover has been smeared: the pixels either side of the sun's edge are close.
        val a = look.backdrop.getPixel(32, 6)
        val b = look.backdrop.getPixel(32, 7)
        assertTrue(Math.abs(Color.red(a) - Color.red(b)) < 40)
    }

    @Test
    fun aGreyCoverStillGivesColours() {
        val grey = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(90, 90, 90)) }
        assertEquals(3, CoverLook.of(grey, 2f)!!.palette.size)
    }

    @Test
    fun theMusicScreenDrawsWithNothingToPress() {
        // The previous, play/pause and next buttons are Android Auto's own, at the corner, so the picture has none.
        shot("music-playing-1600x640", 1600, 640, playing(cover()))
    }

    @Test
    fun pausedAndEmptyScreensDraw() {
        shot("music-paused-800x400", 800, 400, playing(cover(), 20_000, isPlaying = false))
        shot("music-nothing-800x400", 800, 400, null, access = false)
        shot("music-nocover-1600x640", 1600, 640, playing(null))
    }

    @Test
    fun thePanelForTheMapScreenDraws() {
        val painter = MusicPainter(ctx)
        val bmp = Bitmap.createBitmap(640, 700, Bitmap.Config.ARGB_8888)
        painter.renderPanel(Canvas(bmp), RectF(0f, 0f, 640f, 700f), playing(cover()), true, 1_000L)
        System.getenv("SHOTS_DIR")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "music-panel.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        MusicPainter(ctx).renderPanel(Canvas(Bitmap.createBitmap(640, 700, Bitmap.Config.ARGB_8888)), RectF(0f, 0f, 640f, 700f), null, true, 0L)
    }

    @Test
    fun aTitleTooLongToFitSlidesAlongToShowAllOfIt() {
        val long = playing(cover()).copy(title = "An Extremely Long Song Title That Could Never Fit On One Line Of The Car Screen At All")
        val painter = MusicPainter(ctx)
        fun titleStrip(clock: Long): IntArray {
            val bmp = Bitmap.createBitmap(1600, 640, Bitmap.Config.ARGB_8888)
            painter.render(Canvas(bmp), RectF(0f, 0f, 1600f, 640f), long, true, clock)
            val px = IntArray(800 * 70)
            bmp.getPixels(px, 0, 800, 600, 170, 800, 70) // the title's row
            return px
        }
        val atStart = titleStrip(0L)
        val stillWaiting = titleStrip(500L)
        val later = titleStrip(5_000L)
        assertTrue("it holds still at first", atStart.contentEquals(stillWaiting))
        assertTrue("then it slides", !atStart.contentEquals(later))
        // A short title never moves.
        val short = playing(cover())
        fun shortStrip(clock: Long): IntArray {
            val bmp = Bitmap.createBitmap(1600, 640, Bitmap.Config.ARGB_8888)
            painter.render(Canvas(bmp), RectF(0f, 0f, 1600f, 640f), short, true, clock)
            val px = IntArray(800 * 70)
            bmp.getPixels(px, 0, 800, 600, 170, 800, 70)
            return px
        }
        assertTrue(shortStrip(0L).contentEquals(shortStrip(9_000L)))
    }
}
