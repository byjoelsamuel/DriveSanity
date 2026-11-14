package io.github.byjoelsamuel.drivesanity.car

import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import io.github.byjoelsamuel.drivesanity.Prefs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenPickerTest {
    @Test
    fun listsEveryScreenWithTheCurrentOneSelected() {
        val t = ScreenPickerScreen.template(Prefs.Layout.DIAG) {} as ListTemplate
        val list = t.singleList!!
        assertEquals(Prefs.Layout.entries.size, list.items.size)
        assertEquals(Prefs.Layout.entries.indexOf(Prefs.Layout.DIAG), list.selectedIndex)
        assertEquals(Prefs.Layout.entries.map { it.title }, list.items.map { (it as Row).title.toString() })
    }

    @Test
    fun choosingARowPicksThatScreen() {
        var picked: Prefs.Layout? = null
        val t = ScreenPickerScreen.template(Prefs.Layout.GAUGES) { picked = it } as ListTemplate
        t.singleList!!.onSelectedDelegate!!.sendSelected(Prefs.Layout.entries.indexOf(Prefs.Layout.LAUNCH), object : OnDoneCallback {
            override fun onSuccess(response: androidx.car.app.serialization.Bundleable?) {}
            override fun onFailure(response: androidx.car.app.serialization.Bundleable) {}
        })
        assertEquals(Prefs.Layout.LAUNCH, picked)
    }
}
