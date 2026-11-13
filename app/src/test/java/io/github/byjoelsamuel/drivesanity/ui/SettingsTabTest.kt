package io.github.byjoelsamuel.drivesanity.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy

/** Opens Settings, taps through to a page and back. Set SHOTS_DIR to also save pictures of each page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsTabTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions: PhoneActions = Proxy.newProxyInstance(
        PhoneActions::class.java.classLoader, arrayOf(PhoneActions::class.java),
    ) { p, m, a ->
        when (m.name) {
            "equals" -> p === a?.firstOrNull()
            "hashCode" -> 0
            "toString" -> "FakeActions"
            else -> when (m.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                else -> null
            }
        }
    } as PhoneActions

    private var draft: SettingsDraft? = null

    private fun show() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val d = SettingsDraft(ctx).also { draft = it }
        rule.setContent {
            DriveTheme {
                Box(Modifier.fillMaxSize().background(Ui.bg)) {
                    SettingsTab(DriveEngine.snapshot(), d, null, 0L, actions)
                }
            }
        }
    }

    private fun shot(name: String) {
        val dir = System.getenv("SHOTS_DIR") ?: return
        rule.waitForIdle()
        // Draw the window by hand; the test framework's own screen capture waits on a real display.
        val view = rule.activity.findViewById<android.view.View>(android.R.id.content)
        val w = (411 * 2.0).toInt()
        val h = (891 * 2.0).toInt()
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun unsavedChangesShowTheBar() {
        show()
        rule.onNodeWithText("Units").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("km/h · °C · km").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("1 unsaved change").onFirst().assertIsDisplayed()
        shot("unsaved-bar")
        rule.onNodeWithText("Apply").performClick()
        rule.waitForIdle()
        shot("apply-dialog")
    }

    @Test
    fun systemBackReturnsToTheList() {
        show()
        rule.onNodeWithText("Units").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("km/h · °C · km").assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithText("Car and sensors", ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun rowsOpenTheirPagesAndBackReturns() {
        show()
        rule.onNodeWithText("Car and sensors", ignoreCase = true).assertIsDisplayed()
        shot("home")
        val pages = listOf(
            "My car" to "Redline", "Calibration" to "Calibrate", "Units" to "km/h · °C · km",
            "OBD2 adapter" to "Setting up", "Fault codes" to "Read codes again",
            "Battery and read-only" to "Let the car sleep", "Car screens" to "Preview",
            "Map look" to "Classic", "Alerts and shift light" to "banner for a hot engine",
            "Startup" to "Startup animation", "Music" to "How it works", "Storage and data" to "Map cache", "Demo and recordings" to "Start demo",
            "Android Auto" to "UnknownAuto", "About" to "Report a problem",
        )
        for ((row, marker) in pages) {
            rule.onNodeWithText(row).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onAllNodesWithText(marker, substring = true, ignoreCase = true).onFirst().assertIsDisplayed()
            shot("page-" + row.lowercase().replace(Regex("[^a-z0-9]+"), "-"))
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitForIdle()
            rule.onNodeWithText("Car and sensors", ignoreCase = true).assertIsDisplayed()
        }
    }
}
