package io.github.byjoelsamuel.drivesanity.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the launcher icon as a phone would (round mask) and checks it isn't blank. SHOTS_DIR saves the picture. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LauncherIconTest {
    @Test
    fun theLauncherIconDrawsAndIsNotBlank() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val icon = ResourcesCompat.getDrawable(ctx.resources, R.mipmap.ic_launcher, null)!!
        val s = 432
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW) })
        icon.setBounds(0, 0, s, s)
        icon.draw(c)
        val centre = bmp.getPixel(s / 2, s / 2)
        val corner = bmp.getPixel(s / 2, s / 12)
        assertTrue("icon is blank", (centre ushr 24) == 255 && (corner ushr 24) == 255 && centre != corner)
        System.getenv("SHOTS_DIR")?.let { dir ->
            File(dir).mkdirs()
            File(dir, "launcher-icon.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
