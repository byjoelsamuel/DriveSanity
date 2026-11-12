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
    enum class Link { OFF, NOT_SET, NO_PERMISSION, BLUETOOTH_OFF, CONNECTING, LIVE, RETRYING, SLEEPING }

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
        if (!Prefs.advancedReads(app)) {
            probeResult = "Turn on \"Manufacturer-specific reads\" in Setup first. They're still only reads, but they're off by default."
            return
        }
        probeRequested = true
    }

    private val readings = ConcurrentHashMap<String, Pair<Float, Long>>()

    // --- demo mode: the readings come from a recording instead of a car -------------------------

    @Volatile var demo = false
        private set

    fun setDemo(on: Boolean) {
        demo = on
        readings.clear()
        status = if (on) Status(Link.LIVE, "Demo") else Status(Link.OFF)
        if (!on && running) restart()
    }

    /** A reading from the demo or a replay, as if the car had sent it. */
    fun inject(name: String, v: Float) = put(name, v)

    /** Called on the OBD2 thread with the car's speed (km/h as m/s) every time a new one arrives. */
    @Volatile var onSpeed: ((Long, Float) -> Unit)? = null

    // --- letting the car sleep --------------------------------------------------------------
    // Asking a car questions keeps its modules awake, which flattens the battery and stops a dashcam
    // from going into parking mode. So once the engine has been off a while, nothing is asked at all
    // (the adapter's own voltage reading doesn't touch the car), and it wakes when the app is opened,
    // the car moves, or the voltage shows the engine is running.
    @Volatile private var wakeRequested = false
    @Volatile private var retryDelayMs = RETRY_MS

    fun wake() {
        wakeRequested = true
    }
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
        if (v == null) return
        val now = SystemClock.elapsedRealtime()
        readings[key] = v to now
        io.github.byjoelsamuel.drivesanity.demo.RawRecorder.obd(key, v)
        if (key == "speed") onSpeed?.invoke(now, v / 3.6f)
    }

    val rpm get() = value("rpm")
    val speedKmh get() = value("speed")

    /** The car's speed only while it's being read often, which is when it beats GPS (one fix a second). */
    val speedLiveKmh get() = value("speed", 1_200)
    val coolantC get() = value("coolant", 15_000)
    val intakeC get() = value("intake", 15_000)
    val throttlePct get() = value("throttle")

    // The pedal: the car's own pedal reading when it has one, otherwise the throttle valve rescaled.
    private val pedalPids = listOf(Pid.PEDAL_REL, Pid.PEDAL_D, Pid.PEDAL_E, Pid.THROTTLE_REL)
    @Volatile var pedalSource: String = "not known yet"
        private set
    @Volatile private var pedalPid: Pid? = null
    private val valve = Rescale()

    /** How far down the accelerator is, 0–100. */
    val pedalPct: Float?
        get() {
            if (demo) return value("pedal")
            val pid = pedalPid
            if (pid != null) return value(key(pid))
            return throttlePct?.let { valve.scale(it) }
        }
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
                val asleep = e is CarAsleepException && Prefs.letCarSleep(app)
                status = if (asleep) Status(Link.SLEEPING, "car is off") else Status(Link.RETRYING, e.message ?: e.javaClass.simpleName)
                if (!asleep) reconnects++
                lastProblem = e.message ?: e.javaClass.simpleName
                try {
                    // A car that's off isn't asked again every few seconds: wait longer each time, up to two
                    // minutes, and sooner if the app is opened or the car starts moving.
                    pause(if (asleep) retryDelayMs else RETRY_MS)
                    retryDelayMs = if (asleep) minOf(retryDelayMs * 2, MAX_RETRY_MS) else RETRY_MS
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

    /** Sleeps up to [ms], less if [wake] is called. */
    private fun pause(ms: Long) {
        var left = ms
        while (left > 0 && running) {
            if (wakeRequested) {
                wakeRequested = false
                retryDelayMs = RETRY_MS
                return
            }
            Thread.sleep(250)
            left -= 250
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndPoll() {
        if (demo) {
            Thread.sleep(500)
            return
        }
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
        val session = ObdSession(t) { Prefs.advancedReads(app) }
        live = session
        val id = session.start()
        supported = session.supported
        protocol = session.protocolName
        readCodesRequested = true
        liveDetail = "${saved.name} · $id"
        status = Status(Link.LIVE, liveDetail)
        retryDelayMs = RETRY_MS
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

    private var liveDetail = ""

    /** RPM every round, one of the others in turn, fuel and battery every 10 s. */
    private fun poll(s: ObdSession) {
        // Air flow and the commanded air/fuel ratio feed the fuel economy estimate, so they're always asked for.
        pedalPid = pedalPids.firstOrNull { s.supports(it) }
        pedalSource = pedalPid?.let { "car's pedal reading (PID %02X)".format(it.code) }
            ?: if (s.supports(Pid.THROTTLE)) "estimated from the throttle valve (the car has no pedal reading)" else "none"
        valve.reset()
        // Speed and the pedal come round every other time; the slower readings take turns in between.
        val fast = listOf(Pid.SPEED, pedalPid ?: Pid.THROTTLE).filter { s.supports(it) }
        val slow = listOf(Pid.COOLANT, Pid.LOAD, Pid.INTAKE, Pid.MAF, Pid.LAMBDA)
            .filter { s.supports(it) }
        val others = slow.flatMap { fast + it }.ifEmpty { fast }
        val extras = listOf(Pid.TRIM_SHORT, Pid.TRIM_LONG, Pid.MAP, Pid.TIMING, Pid.AMBIENT, Pid.RUNTIME)
            .filter { s.supports(it) }
        var i = 0
        var slowAt = 0L
        var codesAt = 0L
        var vinTried = false
        var misses = 0
        var quietSince = 0L
        failures = 0
        while (running) {
            if (demo) return
            val rpm = tolerate { s.read(Pid.RPM) }
            put("rpm", rpm)
            // Engine off for a while: stop asking, and wait to be woken.
            val t = SystemClock.elapsedRealtime()
            if (rpm == null || rpm < 100f) {
                if (quietSince == 0L) quietSince = t
                val limit = if (rpm == null) QUIET_NO_ANSWER_MS else QUIET_ENGINE_OFF_MS
                if (Prefs.letCarSleep(app) && t - quietSince >= limit) {
                    sleepUntilWoken(s)
                    quietSince = 0L
                    continue
                }
            } else {
                quietSince = 0L
            }
            val rotation = if (diagnosticsOn) others + extras else others
            if (rotation.isNotEmpty()) {
                val pid = rotation[i++ % rotation.size]
                val v = tolerate { s.read(pid) }
                put(key(pid), v)
                if (pid == Pid.THROTTLE && v != null) valve.see(v)
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

    /**
     * The car is off. Nothing is sent to it; only the adapter's own voltage reading is taken, every few
     * seconds. Returns when something says the car is in use again.
     */
    private fun sleepUntilWoken(s: ObdSession) {
        status = Status(Link.SLEEPING, "car is off")
        wakeRequested = false
        var baseline: Float? = null
        while (running && !demo) {
            val v = tolerate { s.volts() }
            put("volts", v)
            if (v != null) {
                if (baseline == null) baseline = v
                // A running engine charges: the voltage steps up well above where it sat while parked.
                if (v >= RUNNING_VOLTS || v >= baseline + RISE_VOLTS) break
            }
            if (waitOrWake(3_000)) break
        }
        wakeRequested = false
        status = Status(Link.LIVE, liveDetail)
    }

    /** Waits up to [ms]; true if [wake] was called meanwhile. */
    private fun waitOrWake(ms: Long): Boolean {
        var left = ms
        while (left > 0 && running) {
            if (wakeRequested) return true
            Thread.sleep(250)
            left -= 250
        }
        return false
    }

    private const val RETRY_MS = 3_000L
    private const val MAX_RETRY_MS = 120_000L
    private const val QUIET_NO_ANSWER_MS = 20_000L
    private const val QUIET_ENGINE_OFF_MS = 120_000L
    private const val RUNNING_VOLTS = 13.2f
    private const val RISE_VOLTS = 0.5f

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
    } catch (_: IllegalArgumentException) {
        null // a request the guard refused; nothing was sent and nothing is wrong with the link
    }

    private fun key(pid: Pid) = pid.name.lowercase()

    /**
     * Turns the throttle valve's position into something like a pedal: closed at idle reads 0, the
     * widest it has been seen open reads 100. It's an estimate, and shows as one in Diagnostics.
     */
    class Rescale {
        private var lo = Float.MAX_VALUE
        private var hi = 0f

        fun reset() {
            lo = Float.MAX_VALUE
            hi = 0f
        }

        fun see(v: Float) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }

        fun scale(v: Float): Float {
            if (lo == Float.MAX_VALUE) return 0f
            // Until it has seen the valve open a fair way, don't blow a small movement up to 100%.
            val span = maxOf(hi - lo, 40f)
            return ((v - lo) / span * 100f).coerceIn(0f, 100f)
        }
    }

    /** The readings the car sends. */
    fun extra(p: Pid): Float? = value(p.name.lowercase(), 15_000)

    /** Tire pressures in PSI: front left, front right, rear left, rear right. */
    val tirePsi: List<Float?> get() = listOf("tire_fl", "tire_fr", "tire_rl", "tire_rr").map { value(it, 30_000) }

    /** What the car has said, for the Setup screen. */
    fun report(): String = (live?.report() ?: "Not connected, so nothing to report yet.") +
        "Pedal reading: $pedalSource\n" +
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
        Link.SLEEPING -> "OBD2: ${status.detail}, not asking the car so it can sleep"
    }
}
