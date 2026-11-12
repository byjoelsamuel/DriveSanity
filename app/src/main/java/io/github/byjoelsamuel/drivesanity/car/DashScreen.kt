package io.github.byjoelsamuel.drivesanity.car

import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.byjoelsamuel.drivesanity.Colors
import io.github.byjoelsamuel.drivesanity.DashPainter
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.RetroPainter

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

    private val tick = object : Runnable {
        override fun run() {
            draw()
            // ~15 fps is plenty for gauges; the map moves, so it gets 30.
            handler.postDelayed(this, if (Prefs.layout(carContext) == Prefs.Layout.MAP) 33 else 66)
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
            }
        })
    }

    override fun onGetTemplate(): Template {
        shownRecording = DriveEngine.recording
        shownLayout = Prefs.layout(carContext)
        val calibrate = Action.Builder()
            .setTitle(if (DriveEngine.snapshot().calibrating) "Calibrating…" else "Calibrate")
            .setOnClickListener {
                DriveEngine.calibrate()
                CarToast.makeText(carContext, DriveEngine.status, CarToast.LENGTH_SHORT).show()
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
            .setTitle("Show ${Prefs.layout(carContext).next.title}")
            .setOnClickListener {
                Prefs.toggleLayout(carContext)
                invalidate()
            }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(calibrate).addAction(record).addAction(layout).build())
            .build()
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        full = Rect(0, 0, container.width, container.height)
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

    private fun draw() {
        // The phone changed something the buttons show.
        if (DriveEngine.recording != shownRecording || Prefs.layout(carContext) != shownLayout) invalidate()

        val s = surface ?: return
        if (!s.isValid) return
        val canvas = try {
            s.lockCanvas(null)
        } catch (_: Exception) {
            return
        }
        try {
            canvas.drawColor(if (Prefs.layout(carContext) == Prefs.Layout.RETRO) RetroPainter.BG else Colors.BG)
            val area = if (visible.isEmpty) full else visible
            painter.draw(canvas, RectF(area), DriveEngine.snapshot(), Prefs.units(carContext), Prefs.layout(carContext))
        } finally {
            s.unlockCanvasAndPost(canvas)
        }
    }
}
