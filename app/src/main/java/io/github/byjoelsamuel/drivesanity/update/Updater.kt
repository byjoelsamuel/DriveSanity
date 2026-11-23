package io.github.byjoelsamuel.drivesanity.update

import android.content.ContentValues
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.PhoneCheck
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.nav.Http
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Finds out whether a newer DriveSanity has been released on GitHub, and downloads it. The only thing
 * it sends is a request for the latest release's details, which any browser could make. The new APK is
 * handed to Android to install; the app can't install itself silently.
 */
object Updater {
    private const val API = "https://api.github.com/repos/byjoelsamuel/DriveSanity/releases/latest"
    const val PAGE = "https://github.com/byjoelsamuel/DriveSanity/releases/latest"
    private const val APK_NAME = "DriveSanity.apk"
    private const val CHECK_EVERY_MS = 6L * 3600 * 1000

    /** A published release that has the APK attached. */
    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
        /** The file's SHA-256 as GitHub reports it, when it does. */
        val sha256: String?,
        val pageUrl: String,
    )

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release, val done: Long, val total: Long) : Status
        data class Ready(val release: Release, val file: File) : Status
        data class Failed(val message: String, val release: Release?) : Status
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set

    /** True once the person has closed the pop-up this time round, so it doesn't keep coming back. */
    var dismissed by mutableStateOf(false)

    @Volatile private var cancelled = false

    // --- versions ---------------------------------------------------------------------------

    /** "v0.19.0" or "0.19.0" as numbers, or null if it isn't one. */
    fun parseVersion(s: String): List<Int>? {
        val parts = s.trim().removePrefix("v").removePrefix("V").split('.')
        if (parts.size !in 2..4) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** Whether [candidate] is a newer version than [current]. Anything that isn't a version never is. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The release described by GitHub's "latest release" answer, or null if it has no APK. */
    fun parse(json: String): Release? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        if (parseVersion(tag) == null) null else {
            val assets = o.getJSONArray("assets")
            var found: Release? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == APK_NAME) {
                    found = Release(
                        version = tag.trim().removePrefix("v"),
                        notes = o.optString("body", "").trim(),
                        apkUrl = a.getString("browser_download_url"),
                        sizeBytes = a.optLong("size", 0L),
                        sha256 = a.optString("digest", "").removePrefix("sha256:").takeIf { it.length == 64 },
                        pageUrl = o.optString("html_url", PAGE),
                    )
                }
            }
            found
        }
    } catch (_: Exception) {
        null
    }

    // --- checking ---------------------------------------------------------------------------

    /** On opening the app: look, if the person wants that and it hasn't been done lately. */
    fun checkOnOpen(ctx: Context) {
        val app = ctx.applicationContext
        if (!Prefs.autoUpdateCheck(app)) return
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck(app) < CHECK_EVERY_MS) return
        check(app, manual = false)
    }

    /** Looks now. A manual look also shows a version the person had skipped. */
    fun check(ctx: Context, manual: Boolean) {
        val app = ctx.applicationContext
        if (status is Status.Checking || status is Status.Downloading) return
        val before = status
        if (manual) status = Status.Checking
        Thread {
            try {
                val release = parse(String(Http.get(API, 12_000)))
                Prefs.setLastUpdateCheck(app, System.currentTimeMillis())
                status = when {
                    release == null -> if (manual) Status.Failed("Couldn't read the latest release.", null) else before
                    isNewer(release.version, BuildConfig.VERSION_NAME) -> {
                        if (manual || Prefs.skippedUpdate(app) != release.version) dismissed = false
                        Status.Available(release)
                    }
                    else -> Status.UpToDate
                }
            } catch (e: Exception) {
                // Not being able to look (no signal, GitHub busy) only matters if the person asked.
                status = if (manual) Status.Failed("Couldn't reach GitHub. Check your connection and try again.", null) else before
            }
        }.apply { isDaemon = true; name = "update-check" }.start()
    }

    /** "Not now" for a version, for good: the pop-up won't offer it again, though Settings still shows it. */
    fun skip(ctx: Context, release: Release) {
        Prefs.setSkippedUpdate(ctx.applicationContext, release.version)
        dismissed = true
    }

    /** Whether the pop-up should be up. */
    fun shouldPrompt(ctx: Context): Boolean {
        if (dismissed) return false
        return when (val s = status) {
            is Status.Available -> Prefs.skippedUpdate(ctx.applicationContext) != s.release.version
            is Status.Downloading, is Status.Ready, is Status.Failed -> true
            else -> false
        }
    }

    // --- downloading ------------------------------------------------------------------------

    fun cancelDownload() {
        cancelled = true
    }

    fun download(ctx: Context) {
        val app = ctx.applicationContext
        val release = when (val s = status) {
            is Status.Available -> s.release
            is Status.Failed -> s.release ?: return
            else -> return
        }
        cancelled = false
        status = Status.Downloading(release, 0, release.sizeBytes)
        Thread {
            try {
                val dir = File(app.filesDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // older downloads
                val part = File(dir, "DriveSanity-${release.version}.apk.part")
                val out = File(dir, "DriveSanity-${release.version}.apk")
                fetch(release, part)
                if (cancelled) {
                    part.delete()
                    status = Status.Available(release)
                    return@Thread
                }
                release.sha256?.let { want ->
                    if (!sha256(part).equals(want, ignoreCase = true)) {
                        part.delete()
                        status = Status.Failed("The download didn't match what GitHub published, so it was thrown away. Try again.", release)
                        return@Thread
                    }
                }
                if (!part.renameTo(out)) throw IOException("couldn't save the file")
                saveToDownloads(app, out)
                dismissed = false
                status = Status.Ready(release, out)
            } catch (e: Exception) {
                status = Status.Failed("The download stopped: ${e.message ?: "no connection"}. Try again.", release)
            }
        }.apply { isDaemon = true; name = "update-download" }.start()
    }

    private fun fetch(release: Release, to: File) {
        val c = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            var done = 0L
            var lastShown = 0L
            c.inputStream.use { input ->
                to.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        if (cancelled) return
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        val now = SystemClock.uptimeMillis()
                        if (now - lastShown > 120) {
                            lastShown = now
                            status = Status.Downloading(release, done, total)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("the file was cut short")
        } finally {
            c.disconnect()
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** A copy in Downloads, so it can also be opened from UnknownAuto's own file picker. Skipped on old Android. */
    private fun saveToDownloads(app: Context, file: File) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            app.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        } catch (_: Exception) {
            // A copy in Downloads is a nicety; the install doesn't depend on it.
        }
    }

    // --- installing -------------------------------------------------------------------------

    /** Hands the APK to Android's installer, or to any other app the person picks (UnknownAuto, say). */
    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(Intent.createChooser(view, "Install DriveSanity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Opens the APK straight in UnknownAuto, which installs it with the Play mark Android Auto needs.
     * False if UnknownAuto isn't there, so the caller can fall back to the usual chooser.
     */
    fun installWithUnknownAuto(ctx: Context, file: File): Boolean {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .setPackage(PhoneCheck.UNKNOWN_AUTO)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(view)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Sends the APK file to another app, for the people who install with one that takes shared files. */
    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Send the update to…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Lets a test put the updater in the "a new version is out" state. */
    internal fun setAvailableForTest(release: Release) {
        status = Status.Available(release)
        dismissed = false
    }

    /** Back to "not looked yet", used when a new check should start from scratch. */
    internal fun reset() {
        status = Status.Idle
        dismissed = false
        cancelled = false
    }
}
