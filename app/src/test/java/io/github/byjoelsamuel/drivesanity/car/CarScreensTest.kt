package io.github.byjoelsamuel.drivesanity.car

import android.content.Context
import android.os.SystemClock
import androidx.car.app.HandshakeInfo
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Action
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import io.github.byjoelsamuel.drivesanity.media.NowPlaying
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The car screens, built against a stand-in Android Auto: what buttons each one has, and that they do something. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarScreensTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var car: TestCarContext

    private val done = object : OnDoneCallback {}

    @Before
    fun setUp() {
        car = TestCarContext.createCarContext(ctx)
        newAndroidAuto(7)
        MediaWatcher.now = null
    }

    @After
    fun tearDown() {
        MediaWatcher.now = null
        Prefs.setLayout(ctx, Prefs.Layout.GAUGES)
    }

    private fun newAndroidAuto(level: Int) = car.updateHandshakeInfo(HandshakeInfo("com.test.host", level))

    private fun screen(layout: Prefs.Layout): Template {
        Prefs.setLayout(ctx, layout)
        return DashScreen(car).onGetTemplate()
    }

    private fun song(playing: Boolean) = NowPlaying(
        title = "Song", artist = "Artist", album = "", art = null, accent = 0, appIcon = null, appName = "Player",
        playing = playing, positionMs = 0, durationMs = 100_000, basedAtMs = SystemClock.elapsedRealtime(), speed = 1f,
        canPrev = true, canNext = true, pkg = "p",
    )

    private fun titles(actions: List<Action>) = actions.map { it.title?.toString() ?: "(icon)" }

    private fun stripOf(t: Template): List<Action> = when (t) {
        is NavigationTemplate -> t.actionStrip!!.actions
        else -> error("unexpected ${t.javaClass.simpleName}")
    }

    @Test
    fun everyScreenHasThreeShortTopButtonsEndingInScreens() {
        for (layout in Prefs.Layout.entries) {
            val t = titles(stripOf(screen(layout)))
            assertEquals("$layout: three buttons", 3, t.size)
            assertEquals("$layout", "Screens", t.last())
            assertTrue("$layout has Record: $t", t[1].contains("Record"))
            assertTrue("$layout: titles are short enough to read at a glance: $t", t.all { it.length <= 12 })
        }
    }

    @Test
    fun theFirstButtonFitsTheScreen() {
        assertEquals("Calibrate", titles(stripOf(screen(Prefs.Layout.GAUGES))).first())
        assertEquals("Search", titles(stripOf(screen(Prefs.Layout.MAP))).first())
        assertEquals("Read codes", titles(stripOf(screen(Prefs.Layout.DIAG))).first())
        assertEquals("Clear run", titles(stripOf(screen(Prefs.Layout.LAUNCH))).first())
    }

    @Test
    fun screensOpensThePickerAndSearchOpensWhereTo() {
        val manager = car.getCarService(TestScreenManager::class.java)
        stripOf(screen(Prefs.Layout.GAUGES)).last().onClickDelegate!!.sendClick(done)
        assertTrue(manager.screensPushed.last() is ScreenPickerScreen)
        stripOf(screen(Prefs.Layout.MAP)).first().onClickDelegate!!.sendClick(done)
        assertTrue(manager.screensPushed.last() is SearchScreen)
    }

    private fun cornerIcons(t: Template): Int = (t as NavigationTemplate).mapActionStrip?.actions?.size ?: 0

    @Test
    fun musicHasPreviousPlayPauseAndNextAtTheCorner() {
        MediaWatcher.now = song(playing = true)
        val t = screen(Prefs.Layout.MUSIC)
        assertTrue("one plain template, with no panel taking half the screen", t is NavigationTemplate)
        val corner = (t as NavigationTemplate).mapActionStrip!!.actions
        assertEquals(3, corner.size)
        assertTrue("icons only, so they fit at the corner", corner.all { it.icon != null && it.title == null })
        corner.forEach { it.onClickDelegate!!.sendClick(done) } // pressing them with nothing to control must not crash
    }

    @Test
    fun theMapGetsSearchAndMusicButtonsOnlyWhileSomethingIsPlaying() {
        MediaWatcher.now = null
        assertEquals(1, cornerIcons(screen(Prefs.Layout.MAP)))
        MediaWatcher.now = song(playing = true)
        assertEquals(4, cornerIcons(screen(Prefs.Layout.MAP)))
    }

    @Test
    fun playingAndPausingChangesOnlyTheIconNotTheShapeOfTheScreen() {
        MediaWatcher.now = song(playing = true)
        val a = (screen(Prefs.Layout.MUSIC) as NavigationTemplate).mapActionStrip!!.actions.size
        MediaWatcher.now = song(playing = false)
        val b = (screen(Prefs.Layout.MUSIC) as NavigationTemplate).mapActionStrip!!.actions.size
        assertEquals(a, b)
    }

    @Test
    fun theOtherScreensAreUnchanged() {
        for (layout in listOf(Prefs.Layout.GAUGES, Prefs.Layout.RETRO, Prefs.Layout.DIAG, Prefs.Layout.LAUNCH)) {
            assertTrue("$layout", screen(layout) is NavigationTemplate)
        }
    }
}
