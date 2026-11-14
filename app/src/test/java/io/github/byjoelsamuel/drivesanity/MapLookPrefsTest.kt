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
    fun startsSimplified() = assertEquals(MapLook.SIMPLIFIED, Prefs.mapLook(ctx))

    @Test
    fun aSavedModernLookIsKept() {
        Prefs.setMapLook(ctx, MapLook.MODERN)
        assertEquals(MapLook.MODERN, Prefs.mapLook(ctx))
    }

    @Test
    fun theRemovedClassicLookBecomesSimplified() {
        file.edit(commit = true) { putString("map_look", "CLASSIC") }
        assertEquals(MapLook.SIMPLIFIED, Prefs.mapLook(ctx))
        file.edit(commit = true) { remove("map_look"); putBoolean("modern_map", false) }
        assertEquals(MapLook.SIMPLIFIED, Prefs.mapLook(ctx))
        file.edit(commit = true) { putBoolean("modern_map", true) }
        assertEquals(MapLook.MODERN, Prefs.mapLook(ctx))
    }
}
