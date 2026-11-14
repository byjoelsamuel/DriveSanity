package io.github.byjoelsamuel.drivesanity.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.update.Updater
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class UpdateDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun offersTheUpdateAndNotNowClosesIt() {
        Updater.reset()
        Updater.setAvailableForTest(Updater.Release("99.0.0", "## New\n- Faster", "http://x/a.apk", 5_000_000, null, "http://x"))
        rule.setContent { DriveTheme { UpdateDialog() } }
        rule.onNodeWithText("Update available").assertIsDisplayed()
        rule.onNodeWithText("DriveSanity 99.0.0 is out. You have ${BuildConfig.VERSION_NAME}.").assertIsDisplayed()
        rule.onNodeWithText("Update").assertIsDisplayed()
        rule.onNodeWithText("Skip this version").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        rule.waitForIdle()
        assertTrue(Updater.dismissed)
        rule.onNodeWithText("Update available").assertDoesNotExist()
    }
}
