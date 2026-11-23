package io.github.byjoelsamuel.drivesanity.car

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import io.github.byjoelsamuel.drivesanity.R
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.byjoelsamuel.drivesanity.CarHealth
import io.github.byjoelsamuel.drivesanity.Colors
import io.github.byjoelsamuel.drivesanity.DashPainter
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import io.github.byjoelsamuel.drivesanity.RetroPainter
import io.github.byjoelsamuel.drivesanity.StartupPainter
import io.github.byjoelsamuel.drivesanity.StartupSound
import io.github.byjoelsamuel.drivesanity.obd.Obd
import io.github.byjoelsamuel.drivesanity.track.Launch

/**
 * The car screen: the same dashboard as the phone, drawn onto the surface Android Auto gives
 * navigation apps, with Calibrate and Rec buttons in the corner.
 */
class DashScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {
    private val painter = DashPainter(carContext)
    private val handler = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var full = Rect()
    private var visible = Rect()
    private var shownRecording = false
    private var shownLayout = Prefs.Layout.GAUGES
    private var shownPlaying: Boolean? = null
    private val intro = StartupPainter(carContext)
    private var introStartMs: Long? = null
    private var introPlayed = false

    private val tick = object : Runnable {
        override fun run() {
            val started = SystemClock.uptimeMillis()
            // One bad frame must never take the car screen (and the whole app) down with it.
            try {
                draw()
            } catch (e: Exception) {
                CarHealth.error(carContext, "car screen", e)
            }
            // About 30 frames a second on every screen, counting the time the drawing took.
            handler.postDelayed(this, maxOf(4L, FRAME_MS - (SystemClock.uptimeMillis() - started)))
        }
    }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.post(tick)
            }

            override fun onStop(owner: LifecycleOwner) {
                handler.removeCallbacks(tick)
                Obd.wantDiagnostics("car", false)
            }
        })
    }

    override fun onGetTemplate(): Template = try {
        buildTemplate()
    } catch (e: Exception) {
        // If the full set of buttons can't be built, still show a working screen with the one that matters.
        CarHealth.error(carContext, "car buttons", e)
        NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Screens").setOnClickListener { screenManager.push(ScreenPickerScreen(carContext)) }.build())
                    .build(),
            )
            .build()
    }

    private fun buildTemplate(): Template {
        shownRecording = DriveEngine.recording
        val now = Prefs.layout(carContext)
        shownLayout = now
        shownPlaying = MediaWatcher.now?.playing
        val onDiag = now == Prefs.Layout.DIAG
        val onLaunch = now == Prefs.Layout.LAUNCH
        val onMusic = now == Prefs.Layout.MUSIC
        val onMap = now == Prefs.Layout.MAP
        val countdown = Prefs.launchCountdown(carContext)
        val first = Action.Builder()
            .setTitle(
                when {
                    onMap -> "Search"
                    onDiag -> "Read codes"
                    onLaunch -> if (countdown) "Start 3-2-1" else "Clear run"
                    DriveEngine.snapshot().calibrating -> "Calibrating…"
                    else -> "Calibrate"
                },
            )
            .setOnClickListener {
                if (onMap) {
                    // The map has no gauges to calibrate; its first button is "Where to?" (keyboard and voice).
                    screenManager.push(SearchScreen(carContext))
                } else if (onDiag) {
                    Obd.requestReadCodes()
                    CarToast.makeText(carContext, "Reading fault codes…", CarToast.LENGTH_SHORT).show()
                } else if (onLaunch) {
                    if (countdown) {
                        Launch.startCountdown()
                        CarToast.makeText(carContext, if (Launch.countingDown) "3, 2, 1…" else "Come to a stop first", CarToast.LENGTH_SHORT).show()
                    } else {
                        Launch.reset()
                        CarToast.makeText(carContext, "Cleared", CarToast.LENGTH_SHORT).show()
                    }
                } else {
                    DriveEngine.calibrate()
                    CarToast.makeText(carContext, DriveEngine.status, CarToast.LENGTH_SHORT).show()
                }
            }
            .build()
        val record = Action.Builder()
            .setTitle(if (shownRecording) "● Stop" else "● Record")
            .setOnClickListener {
                DriveEngine.toggleRecording(carContext)
                CarToast.makeText(carContext, DriveEngine.status, CarToast.LENGTH_SHORT).show()
                invalidate()
            }
            .build()
        // One short button for every screen, instead of stepping through them one at a time.
        val layout = Action.Builder()
            .setTitle("Screens")
            .setOnClickListener { screenManager.push(ScreenPickerScreen(carContext)) }
            .build()
        // On the map, a search button over the map opens "Where to?" (keyboard and voice).
        val search = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_search)).build())
            .setOnClickListener { screenManager.push(SearchScreen(carContext)) }
            .build()
        fun iconAction(res: Int, press: () -> Unit) = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build())
            .setOnClickListener { press(); invalidate() }
            .build()
        val mapStrip = when {
            // The music buttons are Android Auto's own, which appear at the corner when the knob is turned or the screen touched; those are the ones a knob can press.
            onMusic -> ActionStrip.Builder()
                .addAction(iconAction(R.drawable.ic_skip_previous) { MediaWatcher.previous() })
                .addAction(iconAction(if (MediaWatcher.now?.playing == true) R.drawable.ic_pause else R.drawable.ic_play) { MediaWatcher.playPause() })
                .addAction(iconAction(R.drawable.ic_skip_next) { MediaWatcher.next() })
                .build()
            // The map keeps search, and the music buttons while something is playing, for the knob.
            now == Prefs.Layout.MAP -> ActionStrip.Builder().addAction(search).apply {
                if (MediaWatcher.now != null) {
                    addAction(iconAction(R.drawable.ic_skip_previous) { MediaWatcher.previous() })
                    addAction(iconAction(if (MediaWatcher.now?.playing == true) R.drawable.ic_pause else R.drawable.ic_play) { MediaWatcher.playPause() })
                    addAction(iconAction(R.drawable.ic_skip_next) { MediaWatcher.next() })
                }
            }.build()
            else -> null
        }
        val strip = ActionStrip.Builder().addAction(first).addAction(record).addAction(layout).build()
        // A car whose screen won't take the side buttons still gets the three at the top.
        if (mapStrip != null) {
            try {
                return NavigationTemplate.Builder().setActionStrip(strip).setMapActionStrip(mapStrip).build()
            } catch (_: IllegalArgumentException) {
            }
        }
        return NavigationTemplate.Builder().setActionStrip(strip).build()
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        full = Rect(0, 0, container.width, container.height)
        if (!introPlayed) {
            if (Prefs.startupAnim(carContext)) introStartMs = SystemClock.elapsedRealtime()
            try {
                StartupSound.play(carContext)
            } catch (e: Exception) {
                CarHealth.error(carContext, "start-up sound", e)
            }
        }
        introPlayed = true
        draw()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        draw()
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        surface = null
    }

    private companion object {
        const val FRAME_MS = 33L
    }

    /** Every way into drawing goes through here, so a failure is recorded, not fatal. */
    private fun draw() {
        try {
            drawNow()
        } catch (e: Exception) {
            CarHealth.error(carContext, "car screen", e)
        }
    }

    private fun drawNow() {
        // The phone changed something the buttons show.
        if (DriveEngine.recording != shownRecording || Prefs.layout(carContext) != shownLayout ||
            ((Prefs.layout(carContext) == Prefs.Layout.MUSIC || Prefs.layout(carContext) == Prefs.Layout.MAP) && MediaWatcher.now?.playing != shownPlaying)
        ) invalidate()

        // The OBD2 screen's extra readings are only asked for while it's showing.
        Obd.wantDiagnostics("car", Prefs.layout(carContext) == Prefs.Layout.DIAG)
        val s = surface ?: return
        if (!s.isValid) return
        val canvas = try {
            s.lockCanvas(null)
        } catch (_: Exception) {
            return
        }
        try {
            drawFrame(canvas)
        } catch (e: Exception) {
            // Say so on the screen, and in Settings → Phone check, instead of crashing.
            CarHealth.error(carContext, "${Prefs.layout(carContext).title} screen", e)
            canvas.drawColor(Colors.BG)
            canvas.drawText("DriveSanity couldn't draw this screen.", full.width() * 0.06f, full.height() * 0.45f, errorPaint)
            canvas.drawText("Pick another one with Screens. Details are in Settings → Phone check.", full.width() * 0.06f, full.height() * 0.55f, errorPaintSmall)
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }

    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.FG; textSize = 44f }
    private val errorPaintSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Colors.DIM; textSize = 30f }

    /** Lets a test make a frame fail, to check the car screen carries on. */
    internal var faultForTest: (() -> Unit)? = null

    private fun drawFrame(canvas: Canvas) {
        faultForTest?.invoke()
        run {
            canvas.drawColor(if (Prefs.layout(carContext) == Prefs.Layout.RETRO) RetroPainter.BG else Colors.BG)
            // Android Auto's "visible area" leaves room at the top for the button strip even while it's
            // hidden, which left a blank band. Draw up to the top edge; the buttons float over it when shown.
            val area = if (visible.isEmpty) full else Rect(visible.left, full.top, visible.right, visible.bottom)
            painter.draw(canvas, RectF(area), DriveEngine.snapshot(), Prefs.units(carContext), Prefs.layout(carContext), RectF(full))
            introStartMs?.let { start ->
                val t = (SystemClock.elapsedRealtime() - start) / 1000f
                if (t >= StartupPainter.DURATION) introStartMs = null else intro.draw(canvas, RectF(full), t)
            }
        }
    }
}
