package io.github.byjoelsamuel.drivesanity.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.byjoelsamuel.drivesanity.Prefs
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdaterTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private var server: ServerSocket? = null

    @Before
    fun fresh() = Updater.reset()

    @After
    fun stop() {
        server?.close()
    }

    @Test
    fun comparesVersionsAsNumbers() {
        assertTrue(Updater.isNewer("0.20.0", "0.19.0"))
        assertTrue(Updater.isNewer("v1.0.0", "0.99.9"))
        assertTrue("0.10.0 is newer than 0.9.0, not older", Updater.isNewer("0.10.0", "0.9.0"))
        assertFalse(Updater.isNewer("0.19.0", "0.19.0"))
        assertFalse(Updater.isNewer("0.18.0", "0.19.0"))
        assertFalse("rubbish is never newer", Updater.isNewer("latest", "0.19.0"))
        assertFalse(Updater.isNewer("0.20", "0.20.0"))
    }

    private fun sampleJson(digest: String? = "sha256:" + "a".repeat(64)) = """
        {"tag_name":"v0.20.0","html_url":"https://github.com/byjoelsamuel/DriveSanity/releases/tag/v0.20.0",
         "body":"## What's new\n- A thing\n- Another thing",
         "assets":[{"name":"other.txt","browser_download_url":"https://x/other.txt","size":1},
                   {"name":"DriveSanity.apk","browser_download_url":"https://x/DriveSanity.apk","size":14680064${if (digest != null) ",\"digest\":\"$digest\"" else ""}}]}
    """.trimIndent()

    @Test
    fun readsAReleaseFromGithubsAnswer() {
        val r = Updater.parse(sampleJson())!!
        assertEquals("0.20.0", r.version)
        assertEquals("https://x/DriveSanity.apk", r.apkUrl)
        assertEquals(14680064L, r.sizeBytes)
        assertEquals("a".repeat(64), r.sha256)
        assertTrue(r.notes.contains("A thing"))
        assertNull("no digest, no hash to check", Updater.parse(sampleJson(null))!!.sha256)
    }

    @Test
    fun aReleaseWithoutTheApkOrAVersionIsIgnored() {
        assertNull(Updater.parse("""{"tag_name":"v0.20.0","assets":[]}"""))
        assertNull(Updater.parse("""{"tag_name":"nightly","assets":[{"name":"DriveSanity.apk","browser_download_url":"u","size":1}]}"""))
        assertNull(Updater.parse("not json"))
    }

    /** A one-file web server on this machine: answers every request with [bytes]. */
    private fun serve(bytes: ByteArray): String {
        val s = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        server = s
        Thread {
            try {
                while (true) {
                    val client = s.accept()
                    client.use { sock ->
                        val reader = sock.getInputStream().bufferedReader()
                        while (reader.readLine().let { it != null && it.isNotEmpty() }) { /* skip the request */ }
                        val out = sock.getOutputStream()
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(bytes)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
        return "http://127.0.0.1:${s.localPort}/DriveSanity.apk"
    }

    private fun release(url: String, bytes: ByteArray, sha: String?) =
        Updater.Release("0.20.0", "notes", url, bytes.size.toLong(), sha, "page")

    private fun hex(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun awaitStatus(done: (Updater.Status) -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end && !done(Updater.status)) Thread.sleep(20)
    }

    @Test
    fun downloadsTheApkAndChecksItsHash() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val rel = release(serve(bytes), bytes, hex(bytes))
        Updater.setAvailableForTest(rel)
        Updater.download(ctx)
        awaitStatus { it is Updater.Status.Ready }
        val ready = Updater.status as Updater.Status.Ready
        assertEquals(bytes.size.toLong(), ready.file.length())
        assertTrue(ready.file.readBytes().contentEquals(bytes))
        assertEquals("only the finished file is left", listOf("DriveSanity-0.20.0.apk"), ready.file.parentFile!!.list()!!.toList())
    }

    @Test
    fun aFileThatDoesNotMatchItsHashIsThrownAway() {
        val bytes = ByteArray(5_000) { 7 }
        val rel = release(serve(bytes), bytes, "b".repeat(64))
        Updater.setAvailableForTest(rel)
        Updater.download(ctx)
        awaitStatus { it is Updater.Status.Failed }
        assertTrue(Updater.status is Updater.Status.Failed)
        assertEquals(0, java.io.File(ctx.filesDir, "updates").list()?.size ?: 0)
    }

    @Test
    fun skippingAVersionStopsThePopupButNotTheStatus() {
        val rel = release("http://x", ByteArray(1), null)
        Updater.setAvailableForTest(rel)
        assertTrue(Updater.shouldPrompt(ctx))
        Updater.skip(ctx, rel)
        assertFalse(Updater.shouldPrompt(ctx))
        assertNotNull((Updater.status as? Updater.Status.Available)?.release)
        Prefs.setSkippedUpdate(ctx, null)
    }
}
