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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        Prefs.setMusicButtonsAlways(ctx, true)
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
        is MapWithContentTemplate -> t.actionStrip!!.actions
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

    @Test
    fun musicAndMapKeepAndroidAutosOwnButtonsOnScreen() {
        for (layout in listOf(Prefs.Layout.MUSIC, Prefs.Layout.MAP)) {
            val t = screen(layout)
            assertTrue("$layout uses the template with content beside the picture", t is MapWithContentTemplate)
            val grid = (t as MapWithContentTemplate).contentTemplate as GridTemplate
            val items = grid.singleList!!.items.map { it as GridItem }
            assertEquals(listOf(MusicPanel.PREVIOUS, MusicPanel.PLAY_PAUSE, MusicPanel.NEXT), items.map { it.title.toString() })
            for (item in items) {
                assertNotNull("${item.title} can be pressed", item.onClickDelegate)
                item.onClickDelegate!!.sendClick(done) // with nothing playing this must simply do nothing
            }
        }
    }

    @Test
    fun theButtonsKeepTheirNamesSoPlayingAndPausingIsOnlyARefresh() {
        fun names(): List<String> {
            val grid = (screen(Prefs.Layout.MUSIC) as MapWithContentTemplate).contentTemplate as GridTemplate
            assertNull("no title on the grid, so it can't change either", grid.title)
            return grid.singleList!!.items.map { (it as GridItem).title.toString() }
        }
        MediaWatcher.now = song(playing = true)
        val whilePlaying = names()
        MediaWatcher.now = song(playing = false)
        assertEquals(whilePlaying, names())
        // And the same on the map screen, so going between the two is a refresh as well.
        val map = (screen(Prefs.Layout.MAP) as MapWithContentTemplate).contentTemplate as GridTemplate
        assertEquals(whilePlaying, map.singleList!!.items.map { (it as GridItem).title.toString() })
    }

    @Test
    fun anOlderAndroidAutoGetsTheButtonsThatAppearOnAKnobTurn() {
        newAndroidAuto(6)
        MediaWatcher.now = song(playing = true)
        val t = screen(Prefs.Layout.MUSIC)
        assertTrue(t is NavigationTemplate)
        assertEquals(3, (t as NavigationTemplate).mapActionStrip!!.actions.size)
    }

    @Test
    fun choosingInThePictureGoesBackToDrawnButtons() {
        Prefs.setMusicButtonsAlways(ctx, false)
        assertTrue(screen(Prefs.Layout.MUSIC) is NavigationTemplate)
        assertTrue(screen(Prefs.Layout.MAP) is NavigationTemplate)
    }

    @Test
    fun theOtherScreensAreUnchanged() {
        for (layout in listOf(Prefs.Layout.GAUGES, Prefs.Layout.RETRO, Prefs.Layout.DIAG, Prefs.Layout.LAUNCH)) {
            assertTrue("$layout", screen(layout) is NavigationTemplate)
        }
    }
}
