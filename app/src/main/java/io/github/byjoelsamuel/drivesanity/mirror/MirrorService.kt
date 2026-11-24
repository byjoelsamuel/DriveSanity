package io.github.byjoelsamuel.drivesanity.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import androidx.core.app.ServiceCompat
import io.github.byjoelsamuel.drivesanity.MainActivity
import io.github.byjoelsamuel.drivesanity.R

/**
 * Copies the phone's screen, as Android's screen-capture permission allows, so the car can show Google Maps.
 * Android makes it a foreground service with a notification, and shows its own "recording" chip while it runs.
 * Frames are scaled down and limited to about 15 a second; nothing is saved or sent anywhere.
 */
class MirrorService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var lastFrameMs = 0L
    private var captureW = 0
    private var captureH = 0
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null || code == 0) {
            fail("Android didn't give the app permission to share the screen.")
            return START_NOT_STICKY
        }
        // Android 14 and later: the service has to be in the foreground before it asks for the projection.
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sharing Google Maps", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        try {
            begin(code, data)
        } catch (e: Exception) {
            fail("Couldn't start sharing: ${e.message ?: e.javaClass.simpleName}")
        }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val p = manager.getMediaProjection(code, data) ?: error("no projection")
        projection = p
        val worker = HandlerThread("map-mirror").also { it.start() }
        thread = worker
        val handler = Handler(worker.looper)
        // Android 14 and later insist on a callback being registered before the capture is created.
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // The person ended it from Android's own chip or notification.
                handler.post { shutDown() }
            }
        }, handler)

        val metrics = realMetrics()
        val (w, h) = MapMirror.captureSize(metrics.widthPixels, metrics.heightPixels)
        val r = newReader(w, h, handler)
        display = p.createVirtualDisplay("DriveSanity Maps", w, h, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, handler)
        // Turning the phone sideways changes the shape of what's copied, so follow it: a landscape picture fills the car screen.
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == 0) followRotation(handler)
            }
        }
        displayListener = listener
        getSystemService(DisplayManager::class.java).registerDisplayListener(listener, handler)
        MapMirror.state = MapMirror.State.Sharing(System.currentTimeMillis())
    }

    private fun realMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(DisplayManager::class.java).getDisplay(0).getRealMetrics(metrics)
        return metrics
    }

    private fun newReader(w: Int, h: Int, handler: Handler): ImageReader {
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        captureW = w
        captureH = h
        r.setOnImageAvailableListener({ source ->
            val image = try {
                source.acquireLatestImage()
            } catch (_: Exception) {
                null
            } ?: return@setOnImageAvailableListener
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastFrameMs < MIN_FRAME_GAP_MS) return@setOnImageAvailableListener
                lastFrameMs = now
                val plane = image.planes[0]
                val bufferWidth = plane.rowStride / plane.pixelStride
                MapMirror.publish(bufferWidth, w, h) { bmp -> bmp.copyPixelsFromBuffer(plane.buffer) }
            } catch (_: Exception) {
                // One bad frame is skipped; the next one replaces it.
            } finally {
                image.close()
            }
        }, handler)
        return r
    }

    /** The phone was turned: copy at the new shape. */
    private fun followRotation(handler: Handler) {
        val vd = display ?: return
        val metrics = realMetrics()
        val (w, h) = MapMirror.captureSize(metrics.widthPixels, metrics.heightPixels)
        if (w == captureW && h == captureH) return
        val old = reader
        val fresh = newReader(w, h, handler)
        vd.resize(w, h, metrics.densityDpi)
        vd.surface = fresh.surface
        runCatching { old?.close() }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MirrorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle("Sharing Google Maps to the car screen")
            .setContentText("Tap Stop when you're done.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun fail(message: String) {
        MapMirror.state = MapMirror.State.Failed(message)
        shutDown(keepState = true)
    }

    private fun shutDown(keepState: Boolean = false) {
        displayListener?.let { runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) } }
        displayListener = null
        runCatching { display?.release() }
        runCatching { reader?.close() }
        runCatching { projection?.stop() }
        thread?.quitSafely()
        display = null
        reader = null
        projection = null
        thread = null
        MapMirror.clear()
        if (!keepState) MapMirror.state = MapMirror.State.Off
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (projection != null) shutDown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mirror"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "io.github.byjoelsamuel.drivesanity.STOP_MIRROR"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val MIN_FRAME_GAP_MS = 66L // about 15 a second

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            ctx.startForegroundService(
                Intent(ctx, MirrorService::class.java).putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data),
            )
        }

        fun stop(ctx: Context) {
            // Reaches the running service so it can let go of the screen capture; harmless if it isn't running.
            runCatching { ctx.startService(Intent(ctx, MirrorService::class.java).setAction(ACTION_STOP)) }
            if (MapMirror.state !is MapMirror.State.Failed) MapMirror.state = MapMirror.State.Off
        }
    }
}
