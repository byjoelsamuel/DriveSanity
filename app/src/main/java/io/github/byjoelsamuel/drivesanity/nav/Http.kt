package io.github.byjoelsamuel.drivesanity.nav

import io.github.byjoelsamuel.drivesanity.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Plain HTTP for the free OpenStreetMap services. They ask every app to say who it is in the
 * User-Agent and to keep its traffic light, so requests go one at a time.
 */
object Http {
    const val USER_AGENT = "DriveSanity/${BuildConfig.VERSION_NAME} (+https://github.com/byjoelsamuel/DriveSanity)"

    val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "net").apply { isDaemon = true } }

    fun get(url: String, timeoutMs: Int = 15_000): ByteArray = request(url, null, timeoutMs)

    fun post(url: String, form: String, timeoutMs: Int = 20_000): ByteArray = request(url, form, timeoutMs)

    private fun request(url: String, form: String?, timeoutMs: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", USER_AGENT)
            if (form != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                c.outputStream.use { it.write(form.toByteArray()) }
            }
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
