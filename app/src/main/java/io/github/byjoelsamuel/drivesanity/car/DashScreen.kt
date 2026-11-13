package io.github.byjoelsamuel.drivesanity.car

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
            draw()
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

    override fun onGetTemplate(): Template {
        shownRecording = DriveEngine.recording
        val now = Prefs.layout(carContext)
        shownLayout = now
        shownPlaying = MediaWatcher.now?.playing
        val onDiag = now == Prefs.Layout.DIAG
        val onLaunch = now == Prefs.Layout.LAUNCH
        val onMusic = now == Prefs.Layout.MUSIC
        val countdown = Prefs.launchCountdown(carContext)
        val first = Action.Builder()
            .setTitle(
                when {
                    onDiag -> "Read codes"
                    onLaunch -> if (countdown) "Start 3-2-1" else "Clear run"
                    DriveEngine.snapshot().calibrating -> "Calibrating…"
                    else -> "Calibrate"
                },
            )
            .setOnClickListener {
                if (onDiag) {
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
        val layout = Action.Builder()
            .setTitle("Show ${now.next.title}")
            .setOnClickListener {
                Prefs.toggleLayout(carContext)
                invalidate()
            }
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
            // The music buttons are the car's own, down the right edge, so the knob and the screen can press them.
            onMusic -> ActionStrip.Builder()
                .addAction(iconAction(R.drawable.ic_skip_previous) { MediaWatcher.previous() })
                .addAction(iconAction(if (MediaWatcher.now?.playing == true) R.drawable.ic_pause else R.drawable.ic_play) { MediaWatcher.playPause() })
                .addAction(iconAction(R.drawable.ic_skip_next) { MediaWatcher.next() })
                .build()
            now == Prefs.Layout.MAP -> ActionStrip.Builder().addAction(search).build()
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
            StartupSound.play(carContext)
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

    private fun draw() {
        // The phone changed something the buttons show.
        if (DriveEngine.recording != shownRecording || Prefs.layout(carContext) != shownLayout ||
            (Prefs.layout(carContext) == Prefs.Layout.MUSIC && MediaWatcher.now?.playing != shownPlaying)
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
            canvas.drawColor(if (Prefs.layout(carContext) == Prefs.Layout.RETRO) RetroPainter.BG else Colors.BG)
            // Android Auto's "visible area" leaves room at the top for the button strip even while it's
            // hidden, which left a blank band. Draw up to the top edge; the buttons float over it when shown.
            val area = if (visible.isEmpty) full else Rect(visible.left, full.top, visible.right, visible.bottom)
            painter.draw(canvas, RectF(area), DriveEngine.snapshot(), Prefs.units(carContext), Prefs.layout(carContext))
            introStartMs?.let { start ->
                val t = (SystemClock.elapsedRealtime() - start) / 1000f
                if (t >= StartupPainter.DURATION) introStartMs = null else intro.draw(canvas, RectF(area), t)
            }
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }
}
