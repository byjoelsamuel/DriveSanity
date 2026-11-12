package io.github.byjoelsamuel.drivesanity.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import io.github.byjoelsamuel.drivesanity.Colors

/** What's playing, as the screens draw it. */
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: Bitmap?,
    /** A colour taken from the cover, for the progress bar and glow. */
    val accent: Int,
    val appIcon: Bitmap?,
    val appName: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** When [positionMs] was true, on the elapsed-realtime clock, and how fast it moves. */
    val basedAtMs: Long,
    val speed: Float,
    val canPrev: Boolean,
    val canNext: Boolean,
    val pkg: String,
) {
    /** Where the song is now, carried on from the last report while it plays. */
    fun positionNow(nowMs: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + ((nowMs - basedAtMs) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Reads whatever the phone is playing (YouTube Music, Spotify, a podcast app, anything that shows
 * playback controls) and passes pause, skip and previous back to it. It needs notification access,
 * which Android asks the person to grant in Settings: that's how any app is allowed to see another
 * app's media session. Nothing is stored or sent anywhere.
 */
object MediaWatcher {
    @Volatile var now: NowPlaying? = null

    /** False until the person has allowed notification access (or in a test that sets it). */
    @Volatile var access = false

    private var controller: MediaController? = null
    private var lastPollMs = 0L
    private var artKey = ""
    private var artBitmap: Bitmap? = null
    private var accent = Colors.ACCENT
    private var iconPkg = ""
    private var iconBitmap: Bitmap? = null
    private var iconName = ""

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Looks again at what's playing; at most twice a second, so it's fine to call every frame. */
    fun poll(ctx: Context) {
        val t = SystemClock.elapsedRealtime()
        if (t - lastPollMs < 500) return
        lastPollMs = t
        val app = ctx.applicationContext
        try {
            val sessions = app.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(app, MediaListener::class.java))
                .filter { it.packageName != app.packageName }
            access = true
            // Prefer what's playing; otherwise whatever was last used and has something loaded.
            val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull { it.metadata != null }
            controller = c
            now = c?.let { read(app, it) }
        } catch (_: SecurityException) {
            access = false
            controller = null
            now = null
        } catch (_: Exception) {
            controller = null
            now = null
        }
    }

    private fun read(app: Context, c: MediaController): NowPlaying? {
        val m = c.metadata ?: return null
        val st = c.playbackState
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val key = "${c.packageName}|$title|$artist|$album"
        if (key != artKey) {
            artKey = key
            artBitmap = cover(app, m)
            accent = artBitmap?.let(::accentOf) ?: Colors.ACCENT
        }
        if (c.packageName != iconPkg) {
            iconPkg = c.packageName
            val pm = app.packageManager
            iconBitmap = runCatching { pm.getApplicationIcon(c.packageName).toBitmap(96) }.getOrNull()
            iconName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault("")
        }
        val actions = st?.actions ?: 0L
        return NowPlaying(
            title = title, artist = artist, album = album, art = artBitmap, accent = accent,
            appIcon = iconBitmap, appName = iconName,
            playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING,
            positionMs = st?.position ?: 0L,
            durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            basedAtMs = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed ?: 1f,
            canPrev = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            pkg = c.packageName,
        )
    }

    private fun cover(app: Context, m: MediaMetadata): Bitmap? {
        listOf(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            .firstNotNullOfOrNull { m.getBitmap(it) }?.let { return it }
        // Some players give an address instead of the picture; only local ones are read.
        for (k in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
            val uri = m.getString(k) ?: continue
            if (!uri.startsWith("content:") && !uri.startsWith("file:")) continue
            runCatching {
                app.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    /** The cover's average colour, lifted a little so it shows on a dark screen. */
    private fun accentOf(b: Bitmap): Int {
        val px = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0)
        return Colors.mix(px or (0xFF shl 24), android.graphics.Color.rgb(222, 228, 236), 0.28f)
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        setBounds(0, 0, size, size)
        draw(c)
        return b
    }

    // --- controls -------------------------------------------------------------------------------

    fun playPause() {
        val c = controller ?: return
        val n = now
        if (n?.playing == true) c.transportControls.pause() else c.transportControls.play()
        // Show it at once rather than waiting for the player to report back.
        n?.let { now = it.copy(playing = !it.playing, positionMs = it.positionNow(), basedAtMs = SystemClock.elapsedRealtime()) }
        lastPollMs = SystemClock.elapsedRealtime() - 250
    }

    fun next() {
        controller?.transportControls?.skipToNext()
        lastPollMs = 0
    }

    /** Previous song, or the start of this one if it's well into it, as the player decides. */
    fun previous() {
        controller?.transportControls?.skipToPrevious()
        lastPollMs = 0
    }
}
