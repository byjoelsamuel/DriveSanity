package io.github.byjoelsamuel.drivesanity.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The warning on Home for a copy Android Auto won't list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class CarInstallBannerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun saysNothingWhenThePlayMarkIsThereOrCantBeTold() {
        rule.setContent {
            DriveTheme {
                CarInstallBanner(true, true, {}, {})
                CarInstallBanner(null, true, {}, {})
            }
        }
        rule.onAllNodesWithText("Android Auto can't see DriveSanity").assertCountEquals(0)
    }

    @Test
    fun explainsAndOpensUnknownAutoWhenItIsInstalled() {
        var opened = 0
        var steps = 0
        rule.setContent { DriveTheme { CarInstallBanner(false, true, { opened++ }, { steps++ }) } }
        rule.onNodeWithText("Android Auto can't see DriveSanity").assertExists()
        rule.onNodeWithText("Open UnknownAuto").performClick()
        rule.onNodeWithText("Show the steps").performClick()
        assertEquals(1, opened)
        assertEquals(1, steps)
    }

    @Test
    fun pointsToTheStepsWhenUnknownAutoIsMissing() {
        var steps = 0
        rule.setContent { DriveTheme { CarInstallBanner(false, false, {}, { steps++ }) } }
        rule.onAllNodesWithText("Open UnknownAuto").assertCountEquals(0)
        rule.onNodeWithText("Show the steps").performClick()
        assertEquals(1, steps)
    }
}
