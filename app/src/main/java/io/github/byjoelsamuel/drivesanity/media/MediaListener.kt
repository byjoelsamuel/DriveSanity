package io.github.byjoelsamuel.drivesanity.media

import android.service.notification.NotificationListenerService

/**
 * Does nothing itself. Android only lets an app read other apps' media sessions if it is an enabled
 * notification listener, so this is what the person switches on in Settings → Notification access. The
 * app never reads, stores or sends any notification.
 */
class MediaListener : NotificationListenerService()
