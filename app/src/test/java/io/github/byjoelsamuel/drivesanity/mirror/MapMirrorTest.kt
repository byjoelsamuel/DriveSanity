package io.github.byjoelsamuel.drivesanity.mirror

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.DashPainter
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Google Maps on the car screen: the picture maths, what the car screen draws, and the manifest entries Android insists on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapMirrorTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tidy() {
        MapMirror.clear()
        MapMirror.state = MapMirror.State.Off
        Prefs.setLayout(ctx, Prefs.Layout.GAUGES)
    }

    @Test
    fun theCaptureIsScaledDownKeepingItsShapeWithEvenSides() {
        assertEquals(576 to 1280, MapMirror.captureSize(1080, 2400))
        assertEquals(720 to 1280, MapMirror.captureSize(720, 1280)) // already small enough
        assertEquals(640 to 1280, MapMirror.captureSize(1440, 2880))
        val (w, h) = MapMirror.captureSize(1081, 2399) // odd sides become even
        assertTrue(w % 2 == 0 && h % 2 == 0)
    }

    @Test
    fun aTallPhoneFitsTheHeightOfAWideScreenAndIsCentred() {
        val car = RectF(0f, 0f, 1280f, 480f)
        val at = MapMirror.fit(540, 1200, car)
        assertEquals(480f, at.height(), 0.5f)
        assertEquals(216f, at.width(), 0.5f) // 540 * 480/1200
        assertEquals(car.centerX(), at.centerX(), 0.5f)
        assertEquals(car.centerY(), at.centerY(), 0.5f)
        assertEquals(540f / 1200f, at.width() / at.height(), 0.001f) // never stretched
        // a picture wider than the space is fitted by width instead
        val wide = MapMirror.fit(1200, 500, RectF(0f, 0f, 600f, 600f))
        assertEquals(600f, wide.width(), 0.5f)
        assertEquals(250f, wide.height(), 0.5f)
    }

    @Test
    fun aFramePaintsIntoTheFitRectAndGoesStale() {
        val paint = Paint()
        val canvas = Canvas(Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888))
        assertNull(MapMirror.draw(canvas, RectF(0f, 0f, 400f, 200f), paint, nowMs = 1_000)) // nothing yet

        // 100x200 picture, 120 pixels per row in the buffer (the extra 20 are padding)
        MapMirror.publish(bufferWidth = 120, w = 100, h = 200, nowMs = 1_000) { it.eraseColor(Color.RED) }
        assertTrue(MapMirror.hasFreshFrame(nowMs = 2_000))
        val out = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val shown = MapMirror.draw(Canvas(out), RectF(0f, 0f, 400f, 200f), paint, nowMs = 2_000)
        assertNotNull(shown)
        assertEquals(100f, shown!!.width(), 0.5f) // fills the height, so 100 wide in the middle
        assertEquals(Color.RED, out.getPixel(200, 100))
        assertEquals(0, out.getPixel(20, 100)) // the band outside is untouched

        // Four seconds with nothing new: it stops showing a stuck picture.
        assertFalse(MapMirror.hasFreshFrame(nowMs = 1_000 + MapMirror.STALE_MS + 1))
        assertNull(MapMirror.draw(canvas, RectF(0f, 0f, 400f, 200f), paint, nowMs = 1_000 + MapMirror.STALE_MS + 1))
    }

    @Test
    fun theGoogleMapsScreenDrawsWithAndWithoutAPictureAtEveryCarSize() {
        val painter = DashPainter(ctx)
        val sizes = listOf(1280 to 480, 1920 to 900, 1920 to 620, 900 to 1600)
        for ((w, h) in sizes) {
            for (withFrame in listOf(false, true)) {
                MapMirror.clear()
                if (withFrame) MapMirror.publish(576, 576, 1280) { it.eraseColor(Color.rgb(40, 120, 200)) }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
                painter.draw(Canvas(bmp), r, DriveEngine.snapshot(), Prefs.units(ctx), Prefs.Layout.MIRROR, r)
                val centre = bmp.getPixel(w / 2, h / 2)
                if (withFrame) assertEquals("the picture should be in the middle at ${w}x$h", Color.rgb(40, 120, 200), centre)
            }
        }
    }

    @Test
    fun aLivePictureSitsInTheMiddleWithTheSpeedBesideIt() {
        // A stand-in for Google Maps' screen: a pale map with a blue route.
        MapMirror.publish(576, 576, 1280) { b ->
            val c = Canvas(b)
            c.drawColor(Color.rgb(232, 228, 218))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.WHITE; p.strokeWidth = 18f
            for (i in 0..6) { c.drawLine(0f, i * 200f, 576f, i * 200f + 60f, p); c.drawLine(i * 100f, 0f, i * 100f - 40f, 1280f, p) }
            p.color = Color.rgb(66, 133, 244); p.strokeWidth = 14f
            c.drawLine(60f, 1200f, 300f, 700f, p); c.drawLine(300f, 700f, 500f, 120f, p)
            p.color = Color.rgb(26, 115, 232); p.style = Paint.Style.FILL
            c.drawRoundRect(0f, 0f, 576f, 190f, 0f, 0f, p)
        }
        val w = 1280
        val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        DashPainter(ctx).draw(Canvas(bmp), r, DriveEngine.snapshot(), Prefs.units(ctx), Prefs.Layout.MIRROR, r)
        System.getenv("SHOTS_DIR")?.let { dir ->
            java.io.File(dir).mkdirs()
            java.io.File(dir, "car-wide-mirror-live.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertEquals(Color.rgb(26, 115, 232), bmp.getPixel(w / 2, 40)) // the top bar of the copied screen
    }

    @Test
    fun androidGetsTheDeclarationsItNeedsToStartTheCapture() {
        val pm = ctx.packageManager
        val svc = pm.getServiceInfo(ComponentName(ctx, MirrorService::class.java), PackageManager.GET_META_DATA)
        // Without this type, Android 14 and later end the app the moment it asks for the screen.
        assertTrue(svc.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION != 0)
        assertFalse(svc.exported)
        val wanted = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in wanted)
        val ask = pm.getActivityInfo(ComponentName(ctx, MirrorConsentActivity::class.java), 0)
        assertFalse(ask.exported)
    }

    @Test
    fun theCarsStopButtonAppearsOnlyWhileSharing() {
        val car = androidx.car.app.testing.TestCarContext.createCarContext(ctx)
        Prefs.setLayout(ctx, Prefs.Layout.MIRROR)
        fun firstButton() = (io.github.byjoelsamuel.drivesanity.car.DashScreen(car).onGetTemplate() as androidx.car.app.navigation.model.NavigationTemplate)
            .actionStrip!!.actions.first().title.toString()
        assertEquals("Calibrate", firstButton())
        MapMirror.state = MapMirror.State.Sharing(0)
        assertEquals("Stop sharing", firstButton())
    }
}
