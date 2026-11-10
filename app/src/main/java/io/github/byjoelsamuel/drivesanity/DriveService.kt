package io.github.byjoelsamuel.drivesanity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * A foreground service with nothing in it but a notification. It tells Android the app is
 * still in use, so GPS keeps coming while you switch to another app on the phone. The work
 * itself happens in [DriveEngine].
 */
class DriveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "While driving", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle("DriveSanity is reading speed and g-force")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, 1, notification, type)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "drive"

        fun start(ctx: Context): Boolean = try {
            ctx.startForegroundService(Intent(ctx, DriveService::class.java))
            true
        } catch (_: Exception) { // not allowed from the background; GPS still works while on screen
            false
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DriveService::class.java))
        }
    }
}
