package io.github.byjoelsamuel.drivesanity.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.byjoelsamuel.drivesanity.nav.GoogleNav

/**
 * What the person switches on in Settings → Notification access. Android only lets an app read other apps'
 * media sessions if it is an enabled notification listener. It is also how the map screen can show Google
 * Maps' next turn: it looks only at Google Maps' own navigation notification, and only for its words. Nothing
 * is stored or sent anywhere, and no other app's notifications are read.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() = look()

    override fun onListenerDisconnected() = GoogleNav.clear()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleNav.PKG) look()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleNav.PKG) look()
    }

    private fun look() {
        try {
            GoogleNav.refresh(activeNotifications)
        } catch (_: Exception) {
            GoogleNav.clear()
        }
    }
}
