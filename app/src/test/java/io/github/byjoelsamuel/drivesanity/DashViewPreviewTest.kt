package io.github.byjoelsamuel.drivesanity

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DashViewPreviewTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun reset() = Prefs.setLayout(ctx, Prefs.Layout.GAUGES)

    private fun draw(view: DashView): IntArray {
        view.layout(0, 0, 800, 400)
        val bmp = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return IntArray(800 * 400).also { bmp.getPixels(it, 0, 800, 0, 0, 800, 400) }
    }

    @Test
    fun thePreviewShowsTheChosenScreenBeforeItIsApplied() {
        Prefs.setLayout(ctx, Prefs.Layout.GAUGES)
        val view = DashView(ctx)
        val applied = draw(view)
        view.layoutOverride = Prefs.Layout.LAUNCH
        val chosen = draw(view)
        assertFalse("a different screen was drawn", applied.contentEquals(chosen))
        view.layoutOverride = null
        assertTrue("and without the override it's back to the applied one", applied.contentEquals(draw(view)))
    }
}
