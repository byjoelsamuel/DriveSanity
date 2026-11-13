package io.github.byjoelsamuel.drivesanity.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.CarProfiles
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.StartupSoundMode
import io.github.byjoelsamuel.drivesanity.Units
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/** What you tap in Settings shows at once, waits for Apply, and Discard and Reset work. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class LiveSettingsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val other get() = CarProfiles.all.first { it.id != CarProfiles.DEFAULT_ID }

    @Before
    fun fresh() {
        // These live in memory between tests, so put them where the app starts.
        DriveEngine.setCar(ctx, CarProfiles.DEFAULT_ID)
        Prefs.setLayout(ctx, Prefs.Layout.GAUGES)
        if (Prefs.units(ctx) != Units.MPH) Prefs.toggleUnits(ctx)
        Prefs.setStartupSound(ctx, StartupSoundMode.VOICE)
        Prefs.setAlerts(ctx, true)
        Prefs.setStartupAnim(ctx, false) // the power-on animation would sit over the screen
    }

    /** The real settings actions, minus the parts that need an activity. */
    private fun actions(): PhoneActions = Proxy.newProxyInstance(PhoneActions::class.java.classLoader, arrayOf(PhoneActions::class.java)) { p, m, a ->
        when (m.name) {
            "setCar" -> DriveEngine.setCar(ctx, a[0] as String)
            "setRedline" -> DriveEngine.setRedline(ctx, a[0] as Float)
            "swapUnits" -> Prefs.toggleUnits(ctx)
            "setCarLayout" -> Prefs.setLayout(ctx, a[0] as Prefs.Layout)
            "setAlerts" -> Prefs.setAlerts(ctx, a[0] as Boolean)
            "setStartupAnim" -> Prefs.setStartupAnim(ctx, a[0] as Boolean)
            "setStartupSound" -> Prefs.setStartupSound(ctx, a[0] as StartupSoundMode)
            "equals" -> return@newProxyInstance p === a[0]
            "hashCode" -> return@newProxyInstance 0
            "toString" -> return@newProxyInstance "Actions"
        }
        if (m.returnType == java.lang.Boolean.TYPE) false else null
    } as PhoneActions

    private fun open(page: String) {
        rule.setContent { DriveTheme { PhoneApp(actions(), startTab = 3) } }
        rule.onNodeWithText(page).performScrollTo().performClick()
        rule.waitForIdle()
        // Time is held still from here, so nothing below can lean on the timer that refreshes the screen.
        rule.mainClock.autoAdvance = false
    }

    private fun frame() = rule.mainClock.advanceTimeBy(200)

    @Test
    fun pickingACarShowsAtOnceAndWaitsForApply() {
        open("My car")
        rule.onNodeWithText(other.name).performScrollTo().performClick()
        frame()
        rule.onNodeWithText(other.name).assertIsSelected()
        rule.onNodeWithText("1 unsaved change").assertIsDisplayed()
        assertEquals("nothing is saved until Apply", CarProfiles.DEFAULT_ID, Prefs.carId(ctx))

        rule.onNodeWithText("Apply").performClick()
        frame()
        rule.onNodeWithText("Apply changes?").assertIsDisplayed()
        assertEquals("still not saved while the box is open", CarProfiles.DEFAULT_ID, Prefs.carId(ctx))
        // The bar's Apply and the box's Apply are both on screen; the box is last.
        rule.onAllNodesWithText("Apply").onLast().performClick()
        rule.mainClock.advanceTimeBy(600)
        assertEquals(other.id, Prefs.carId(ctx))
        rule.onNodeWithText("1 unsaved change").assertDoesNotExist()
        rule.onNodeWithText(other.name).assertIsSelected()
    }

    @Test
    fun discardPutsTheOldChoiceBack() {
        open("Units")
        rule.onNodeWithText("km/h · °C · km").performClick()
        frame()
        rule.onNodeWithText("km/h · °C · km").assertIsSelected()
        rule.onNodeWithText("Discard").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("MPH · °F · mi").assertIsSelected()
        rule.onNodeWithText("1 unsaved change").assertDoesNotExist()
        assertEquals(Units.MPH, Prefs.units(ctx))
    }

    @Test
    fun resetToDefaultOnAPageIsStagedToo() {
        Prefs.setAlerts(ctx, false)
        open("Alerts and shift light")
        rule.onNodeWithText("Reset to default").performClick()
        frame()
        rule.onNodeWithText("1 unsaved change").assertIsDisplayed()
        assertEquals("not applied yet", false, Prefs.alerts(ctx))
    }

    @Test
    fun resetAllAsksFirst() {
        DriveEngine.setCar(ctx, other.id)
        rule.setContent { DriveTheme { PhoneApp(actions(), startTab = 3) } }
        rule.onNodeWithText("Reset all settings").performScrollTo().performClick()
        rule.mainClock.autoAdvance = false
        frame()
        rule.onNodeWithText("Reset all settings?").assertIsDisplayed()
        assertEquals("asking changes nothing", other.id, Prefs.carId(ctx))
    }

    @Test
    fun resetAllPutsEverythingBack() {
        DriveEngine.setCar(ctx, other.id)
        Prefs.setLayout(ctx, Prefs.Layout.MUSIC)
        Prefs.setAlerts(ctx, false)
        Prefs.toggleUnits(ctx)
        val draft = SettingsDraft(ctx)
        draft.resetAll(actions())
        assertEquals(CarProfiles.DEFAULT_ID, Prefs.carId(ctx))
        assertEquals(Prefs.Layout.GAUGES, Prefs.layout(ctx))
        assertEquals(true, Prefs.alerts(ctx))
        assertEquals(Units.MPH, Prefs.units(ctx))
        assertEquals(false, draft.dirty)
    }
}
