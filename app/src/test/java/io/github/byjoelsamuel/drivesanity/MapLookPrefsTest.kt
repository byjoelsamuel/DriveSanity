package io.github.byjoelsamuel.drivesanity

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapLookPrefsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val file get() = ctx.getSharedPreferences("drive", Context.MODE_PRIVATE)

    @Test
    fun startsClassic() = assertEquals(MapLook.CLASSIC, Prefs.mapLook(ctx))

    @Test
    fun aSavedLookIsKept() {
        Prefs.setMapLook(ctx, MapLook.MODERN)
        assertEquals(MapLook.MODERN, Prefs.mapLook(ctx))
        Prefs.setMapLook(ctx, MapLook.SIMPLIFIED)
        assertEquals(MapLook.SIMPLIFIED, Prefs.mapLook(ctx))
    }

    @Test
    fun theOldTrueFalseSettingStillReads() {
        file.edit(commit = true) { putBoolean("modern_map", false) }
        assertEquals(MapLook.CLASSIC, Prefs.mapLook(ctx))
        file.edit(commit = true) { putBoolean("modern_map", true) }
        assertEquals(MapLook.MODERN, Prefs.mapLook(ctx))
    }
}
