package io.github.byjoelsamuel.drivesanity.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.DashPainter
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.StartupSoundMode
import io.github.byjoelsamuel.drivesanity.demo.Demo
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import io.github.byjoelsamuel.drivesanity.media.NowPlaying
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.time.Duration

/**
 * A walk through the app with the demo drive running: every phone tab and every car screen is drawn, and
 * the main buttons are pressed. With SHOTS_DIR set, each screen is saved as a picture to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class WalkthroughTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val looper get() = shadowOf(Looper.getMainLooper())

    @Before
    fun setUp() {
        Prefs.setStartupAnim(ctx, false)
        Prefs.setStartupSound(ctx, StartupSoundMode.OFF)
        Prefs.setAutoUpdateCheck(ctx, false)
        MediaWatcher.holdForTest = true
        MediaWatcher.access = true
        MediaWatcher.now = song()
    }

    /** Recording is a toggle; this says which way, so one test can't leave the next one upside down. */
    private fun record(on: Boolean) {
        if (DriveEngine.recording != on) DriveEngine.toggleRecording(ctx)
    }

    @After
    fun tearDown() {
        record(false)
        Demo.stop()
        MediaWatcher.holdForTest = false
        MediaWatcher.now = null
    }

    private fun cover(): Bitmap {
        val b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.rgb(40, 20, 60))
        p.color = Color.rgb(230, 80, 160)
        c.drawCircle(110f, 120f, 80f, p)
        p.color = Color.rgb(60, 180, 220)
        c.drawRect(170f, 180f, 300f, 300f, p)
        return b
    }

    private fun song() = NowPlaying(
        title = "interruptor (Slowed) with a title that keeps going", artist = "zero pill", album = "", art = cover(),
        accent = Color.rgb(230, 80, 160), appIcon = null, appName = "YouTube Music", playing = true,
        positionMs = 61_000, durationMs = 185_000, basedAtMs = SystemClock.elapsedRealtime(), speed = 1f,
        canPrev = true, canNext = true, pkg = "test",
    )

    /** The phone's actions, wired to the real engine wherever that doesn't need an activity. */
    /** Every action a screen asked for, by name, so a test can check a button did something. */
    private val asked = ArrayList<String>()

    private fun actions(): PhoneActions = Proxy.newProxyInstance(PhoneActions::class.java.classLoader, arrayOf(PhoneActions::class.java)) { p, m, a ->
        if (m.declaringClass == PhoneActions::class.java) asked += m.name
        when (m.name) {
            "calibrate" -> DriveEngine.calibrate()
            "toggleRecording" -> DriveEngine.toggleRecording(ctx)
            "swapUnits" -> Prefs.toggleUnits(ctx)
            "setCarLayout" -> Prefs.setLayout(ctx, a[0] as Prefs.Layout)
            "setCar" -> DriveEngine.setCar(ctx, a[0] as String)
            "setRedline" -> DriveEngine.setRedline(ctx, a[0] as Float)
            "setAlerts" -> Prefs.setAlerts(ctx, a[0] as Boolean)
            "setDemo" -> if (a[0] as Boolean) Demo.start(ctx) else Demo.stop()
            "delete" -> (a[0] as File).delete()
            "equals" -> return@newProxyInstance p === a[0]
            "hashCode" -> return@newProxyInstance 0
            "toString" -> return@newProxyInstance "Actions"
        }
        if (m.returnType == java.lang.Boolean.TYPE) false else null
    } as PhoneActions

    private fun shot(name: String, heightDp: Int = 891) {
        val dir = System.getenv("SHOTS_DIR") ?: return
        // Let the screen's own ten-times-a-second refresh run, so it shows what the engine has now.
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        val view = rule.activity.findViewById<View>(android.R.id.content)
        val w = 411 * 2
        val h = heightDp * 2
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        rule.waitForIdle()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun drive(seconds: Int) = repeat(seconds) { looper.idleFor(Duration.ofSeconds(1)) }

    @Test
    fun phoneTabsWithTheDemoRunning() {
        // First look, before anything is set up.
        rule.setContent { DriveTheme { PhoneApp(actions(), startTab = 0) } }
        shot("phone-1-drive-first-open")
        shot("phone-1-drive-first-open-full", 2300)

        Demo.start(ctx)
        drive(45)
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Demo drive").assertExists()
        rule.onNodeWithText("No location access").assertDoesNotExist()
        shot("phone-2-drive-demo")
        shot("phone-2-drive-demo-full", 2300)

        rule.onAllNodesWithText("0–60").onFirst().performClick()
        shot("phone-3-launch")
        // The 0–60 result can be turned into a picture.
        rule.onNodeWithText("Make a picture").performClick()
        rule.mainClock.advanceTimeBy(800)
        shot("phone-3-launch-full", 2600)

        rule.onAllNodesWithText("Trips").onFirst().performClick()
        shot("phone-4-trips-empty")

        // Record a short trip, then look at Trips and open it.
        rule.onAllNodesWithText("Drive").onFirst().performClick()
        rule.waitForIdle()
        record(true)
        drive(70)
        rule.mainClock.advanceTimeBy(300)
        // The button has to say it's recording, and count up, while it is.
        rule.onNodeWithText("Stop recording", substring = true).assertExists()
        shot("phone-5-drive-recording")
        record(false)
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Start recording").assertExists()
        drive(2)
        rule.onAllNodesWithText("Trips").onFirst().performClick()
        drive(2)
        shot("phone-6-trips-one")
        shot("phone-6-trips-one-full", 1800)

        // Open the trip, then its picture maker.
        rule.onNodeWithText("Open").performClick()
        drive(2)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("‹ Trips").assertExists()
        shot("phone-7-trip-detail-full", 2600)
    }

    @Test
    fun carScreensWithTheDemoRunning() {
        Demo.start(ctx)
        drive(50)
        val dir = System.getenv("SHOTS_DIR")
        for ((label, w, h) in listOf(Triple("wide", 1280, 480), Triple("tall", 1920, 900))) {
            for (buttons in listOf(true, false)) {
                for (layout in Prefs.Layout.entries) {
                    val isMusicish = layout == Prefs.Layout.MUSIC || layout == Prefs.Layout.MAP
                    if (!buttons && !isMusicish) continue
                    // With Android Auto's buttons beside it, the picture gets roughly the right-hand 60%.
                    val pw = if (!buttons) (w * 0.6f).toInt() else w
                    val painter = DashPainter(ctx)
                    painter.musicButtonsDrawn = buttons
                    val bmp = Bitmap.createBitmap(pw, h, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    c.drawColor(Color.rgb(18, 20, 22))
                    // A few frames, so the smoothed numbers settle.
                    repeat(40) { painter.draw(c, RectF(0f, 0f, pw.toFloat(), h.toFloat()), DriveEngine.snapshot(), Prefs.units(ctx), layout) }
                    if (dir != null) {
                        File(dir).mkdirs()
                        val name = "car-$label-${layout.name.lowercase()}${if (!buttons) "-with-aa-buttons" else ""}.png"
                        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                }
            }
        }
    }

    @Test
    fun everyButtonOnTheDriveAndLaunchAndTripsTabsDoesSomething() {
        rule.setContent { DriveTheme { PhoneApp(actions(), startTab = 0) } }
        rule.mainClock.advanceTimeBy(300)

        // Drive, before anything is set up.
        rule.onNodeWithText("Calibrate").performClick()
        rule.onNodeWithText("Navigate").performClick()
        rule.onNodeWithText("No location access").performClick()
        rule.onNodeWithText("OBD2 not connected").performClick()
        rule.onNodeWithText("Start recording").performClick()
        rule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertEquals(listOf("calibrate", "navigate", "askLocation", "pickObd", "toggleRecording"), asked)
        // "Not calibrated" takes you to Settings.
        rule.onNodeWithText("Not calibrated").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Car and sensors", ignoreCase = true).assertExists()

        // 0–60: the countdown switch brings up the Start button, and both buttons press.
        rule.onAllNodesWithText("0–60").onFirst().performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("On").performClick()
        rule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertTrue(Prefs.launchCountdown(ctx))
        rule.onNodeWithText("Start 3-2-1").performClick()
        rule.onNodeWithText("Clear run").performClick()
        rule.onNodeWithText("Off").performClick()
        rule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertFalse(Prefs.launchCountdown(ctx))

        // Trips: a recording can be opened, and deleting asks first.
        Demo.start(ctx)
        record(false) // the Start recording press above began one with nothing in it
        record(true)
        drive(8)
        record(false)
        rule.onAllNodesWithText("Trips").onFirst().performClick()
        drive(2)
        rule.mainClock.advanceTimeBy(1200)
        rule.onNodeWithText("Reset trip").performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Cancel").performClick()
        rule.mainClock.advanceTimeBy(300)
        asked.clear()
        // Two recordings are listed (the Start recording press made a short one), so take the first of each.
        rule.onAllNodesWithText("Share").onFirst().performClick()
        rule.onAllNodesWithText("Delete").onFirst().performClick()
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithText("Delete this recording?").assertExists()
        org.junit.Assert.assertEquals("nothing is deleted until it's confirmed", listOf("share"), asked)
        rule.onAllNodesWithText("Delete").onLast().performClick()
        rule.mainClock.advanceTimeBy(300)
        org.junit.Assert.assertEquals(listOf("share", "delete"), asked)
    }
}
