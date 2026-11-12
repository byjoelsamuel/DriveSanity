package io.github.byjoelsamuel.drivesanity.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import io.github.byjoelsamuel.drivesanity.Prefs
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps a connection to the OBD2 adapter the user picked, on its own thread, and the latest
 * readings. Once an adapter is picked it connects by itself whenever the app is running, and
 * keeps retrying every few seconds (the car may be off, or the adapter out of range).
 */
@SuppressLint("StaticFieldLeak") // only ever holds the application context
object Obd {
    enum class Link { OFF, NOT_SET, NO_PERMISSION, BLUETOOTH_OFF, CONNECTING, LIVE, RETRYING }

    data class Status(val link: Link, val detail: String = "")

    @Volatile var status = Status(Link.OFF)
        private set

    /** PIDs the car said it supports, once connected. */
    @Volatile var supported: Set<Int> = emptySet()
        private set

    /** How many times the link dropped and was set up again since the app started, and why the last time. */
    @Volatile var reconnects = 0
        private set
    @Volatile var lastProblem: String? = null
        private set

    // The OBD2 screen's extra readings and fault codes are only asked for while something shows them.
    @Volatile private var diagFromCar = false
    @Volatile private var diagFromPhone = false
    private val diagnosticsOn get() = diagFromCar || diagFromPhone

    fun wantDiagnostics(from: String, on: Boolean) {
        if (from == "car") diagFromCar = on else diagFromPhone = on
    }

    @Volatile var dtcReport: DtcReport? = null
        private set
    @Volatile var vin: String? = null
        private set
    @Volatile var protocol: String = ""
        private set
    @Volatile var probeResult: String? = null
        private set
    @Volatile private var readCodesRequested = false
    @Volatile private var probeRequested = false

    fun requestReadCodes() {
        readCodesRequested = true
    }

    fun requestProbe() {
        probeRequested = true
    }

    private val readings = ConcurrentHashMap<String, Pair<Float, Long>>()
    private lateinit var app: Context
    @Volatile private var running = false
    @Volatile private var worker: Thread? = null
    @Volatile private var link: ObdTransport? = null
    @Volatile private var live: ObdSession? = null

    fun start(context: Context) {
        app = context.applicationContext
        if (running) return
        running = true
        worker = Thread(::loop, "obd").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        link?.close()
        worker?.interrupt()
        worker = null
        readings.clear()
        status = Status(Link.OFF)
    }

    /** After the user picks or forgets an adapter. */
    fun restart() {
        if (!running) return
        stop()
        start(app)
    }

    // --- readings ---------------------------------------------------------------------------

    private fun value(key: String, maxAgeMs: Long = 3_000): Float? {
        val (v, at) = readings[key] ?: return null
        return if (SystemClock.elapsedRealtime() - at <= maxAgeMs) v else null
    }

    private fun put(key: String, v: Float?) {
        if (v != null) readings[key] = v to SystemClock.elapsedRealtime()
    }

    val rpm get() = value("rpm")
    val speedKmh get() = value("speed")
    val coolantC get() = value("coolant", 15_000)
    val intakeC get() = value("intake", 15_000)
    val throttlePct get() = value("throttle")
    val loadPct get() = value("load")
    val fuelPct get() = value("fuel", 60_000)
    val volts get() = value("volts", 20_000)

    /** Air flow into the engine in g/s, and the air/fuel ratio the computer is asking for (1.0 = ideal). */
    val maf get() = value("maf", 3_000)
    val lambda get() = value("lambda", 6_000)

    // --- the connection thread --------------------------------------------------------------

    private fun loop() {
        while (running) {
            try {
                connectAndPoll()
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                if (!running) break
                status = Status(Link.RETRYING, e.message ?: e.javaClass.simpleName)
                reconnects++
                lastProblem = e.message ?: e.javaClass.simpleName
                try {
                    Thread.sleep(3_000)
                } catch (_: InterruptedException) {
                    break
                }
            } finally {
                link?.close()
                link = null
                live = null
                supported = emptySet()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndPoll() {
        val saved = Prefs.obdDevice(app)
        if (saved == null) {
            status = Status(Link.NOT_SET)
            Thread.sleep(2_000)
            return
        }
        if (!hasBluetoothPermission(app)) {
            status = Status(Link.NO_PERMISSION)
            Thread.sleep(2_000)
            return
        }
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            status = Status(Link.BLUETOOTH_OFF)
            Thread.sleep(3_000)
            return
        }
        status = Status(Link.CONNECTING, saved.name)
        adapter.cancelDiscovery()
        val device = adapter.getRemoteDevice(saved.address)
        val t = open(device, saved.ble)
        link = t
        val session = ObdSession(t)
        live = session
        val id = session.start()
        supported = session.supported
        protocol = session.protocolName
        readCodesRequested = true
        status = Status(Link.LIVE, "${saved.name} · $id")
        poll(session)
    }

    @SuppressLint("MissingPermission")
    private fun open(device: BluetoothDevice, ble: Boolean): ObdTransport {
        if (ble) return BleTransport(app, device)
        return try {
            ClassicTransport(device)
        } catch (e: IOException) {
            // Some adapters do both; if the classic link won't open, try low energy.
            if (device.type == BluetoothDevice.DEVICE_TYPE_DUAL) BleTransport(app, device) else throw e
        }
    }

    /** RPM every round, one of the others in turn, fuel and battery every 10 s. */
    private fun poll(s: ObdSession) {
        // Air flow and the commanded air/fuel ratio feed the fuel economy estimate, so they're always asked for.
        val others = listOf(Pid.SPEED, Pid.THROTTLE, Pid.COOLANT, Pid.LOAD, Pid.INTAKE, Pid.MAF, Pid.LAMBDA)
            .filter { s.supports(it) }
        val extras = listOf(Pid.TRIM_SHORT, Pid.TRIM_LONG, Pid.MAP, Pid.TIMING, Pid.AMBIENT, Pid.RUNTIME)
            .filter { s.supports(it) }
        var i = 0
        var slowAt = 0L
        var codesAt = 0L
        var vinTried = false
        var misses = 0
        failures = 0
        while (running) {
            val rpm = tolerate { s.read(Pid.RPM) }
            put("rpm", rpm)
            val rotation = if (diagnosticsOn) others + extras else others
            if (rotation.isNotEmpty()) {
                val pid = rotation[i++ % rotation.size]
                put(key(pid), tolerate { s.read(pid) })
            }
            val now = SystemClock.elapsedRealtime()
            if (now >= slowAt) {
                put("fuel", tolerate { s.read(Pid.FUEL) })
                put("volts", tolerate { s.volts() })
                slowAt = now + 10_000
            }
            if (readCodesRequested || (diagnosticsOn && now - codesAt > 60_000)) {
                readCodesRequested = false
                codesAt = now
                tolerate { s.readDtcReport() }?.let { dtcReport = it }
            }
            if (diagnosticsOn && vin == null && !vinTried) {
                vinTried = true
                vin = tolerate { s.vin() }
            }
            if (probeRequested) {
                probeRequested = false
                probeResult = tolerate { s.probe().joinToString("\n") }
            }
            // The adapter stays powered with the engine off; stop hammering a sleeping car.
            misses = if (rpm == null && s.supports(Pid.RPM)) misses + 1 else 0
            if (misses >= 5) Thread.sleep(1_000)
        }
    }

    private var failures = 0

    /**
     * One slow or lost reply isn't a lost connection. Shrug it off, and only give up (which means
     * setting the whole link up again, a protocol search included) after four in a row.
     */
    private fun <T> tolerate(block: () -> T?): T? = try {
        block().also { failures = 0 }
    } catch (e: IOException) {
        if (++failures >= 4) throw e
        null
    }

    private fun key(pid: Pid) = pid.name.lowercase()

    /** The readings the car sends. */
    fun extra(p: Pid): Float? = value(p.name.lowercase(), 15_000)

    /** Tire pressures in PSI: front left, front right, rear left, rear right. */
    val tirePsi: List<Float?> get() = listOf("tire_fl", "tire_fr", "tire_rl", "tire_rr").map { value(it, 30_000) }

    /** What the car has said, for the Setup screen. */
    fun report(): String = (live?.report() ?: "Not connected, so nothing to report yet.") +
        "Link dropped and restarted: $reconnects times since the app opened\n" +
        (lastProblem?.let { "Last problem: $it\n" } ?: "") +
        (probeResult?.let { "\nMazda-specific probe:\n$it\n" } ?: "")

    fun hasBluetoothPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** One line for the screens. */
    fun describe(): String = when (status.link) {
        Link.OFF -> "OBD2 off"
        Link.NOT_SET -> "OBD2: tap OBD2 to pick your adapter"
        Link.NO_PERMISSION -> "OBD2: allow Bluetooth (tap OBD2)"
        Link.BLUETOOTH_OFF -> "OBD2: Bluetooth is off"
        Link.CONNECTING -> "OBD2: connecting to ${status.detail}…"
        Link.LIVE -> "OBD2: ${status.detail}"
        Link.RETRYING -> "OBD2: ${status.detail} (retrying)"
    }
}
