package io.github.byjoelsamuel.drivesanity.obd

import java.io.Closeable
import java.io.IOException

/** The OBD2 readings the app asks for, mode 01. Formulas are from the SAE J1979 standard. */
enum class Pid(val code: Int, val bytes: Int, private val decode: (IntArray) -> Float) {
    RPM(0x0C, 2, { (it[0] * 256 + it[1]) / 4f }),
    SPEED(0x0D, 1, { it[0].toFloat() }),               // km/h
    COOLANT(0x05, 1, { it[0] - 40f }),                  // °C
    INTAKE(0x0F, 1, { it[0] - 40f }),                   // °C
    THROTTLE(0x11, 1, { it[0] * 100f / 255f }),         // %
    LOAD(0x04, 1, { it[0] * 100f / 255f }),             // %
    FUEL(0x2F, 1, { it[0] * 100f / 255f }),            // % of the tank

    // What the driver's foot is doing, if the car reports it. Throttle position (0x11) is the valve,
    // which a modern engine moves on its own to hold the revs, so it isn't the pedal.
    PEDAL_REL(0x5A, 1, { it[0] * 100f / 255f }),        // % relative accelerator pedal position
    PEDAL_D(0x49, 1, { it[0] * 100f / 255f }),          // % accelerator pedal position D
    PEDAL_E(0x4A, 1, { it[0] * 100f / 255f }),          // % accelerator pedal position E
    THROTTLE_REL(0x45, 1, { it[0] * 100f / 255f }),     // % relative throttle position (0 = closed)

    // Extra readings, asked for only while the OBD2 screen is showing.
    TRIM_SHORT(0x06, 1, { (it[0] - 128) * 100f / 128f }), // %
    TRIM_LONG(0x07, 1, { (it[0] - 128) * 100f / 128f }),  // %
    MAP(0x0B, 1, { it[0].toFloat() }),                    // kPa
    TIMING(0x0E, 1, { it[0] / 2f - 64f }),                // degrees before top dead centre
    MAF(0x10, 2, { (it[0] * 256 + it[1]) / 100f }),       // g/s of air
    RUNTIME(0x1F, 2, { (it[0] * 256 + it[1]).toFloat() }), // seconds since the engine started
    LAMBDA(0x44, 2, { (it[0] * 256 + it[1]) / 32768f }),  // commanded air/fuel, 1.0 = ideal
    AMBIENT(0x46, 1, { it[0] - 40f });                    // °C outside

    val command get() = "01%02X".format(code)

    fun value(data: IntArray): Float? = if (data.size >= bytes) decode(data) else null
}

/** Reading what an ELM327 adapter sends back. */
object Elm {
    private val errors = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS INIT", "BUS ERROR", "BUS BUSY",
        "STOPPED", "DATA ERROR", "FB ERROR", "LV RESET", "BUFFER FULL", "ERROR",
    )

    /** The reply's lines, without the prompt, blank lines or the "SEARCHING..." the first query prints. */
    fun lines(raw: String): List<String> =
        raw.replace(">", "").split('\r', '\n').map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("SEARCHING") }

    private fun hexBytes(hex: String): List<Int> = hex.chunked(2).filter { it.length == 2 }.map { it.toInt(16) }

    /**
     * The bytes of each ECU's reply. Handles one-line replies and the adapter's multi-line form:
     * a byte-count line ("014"), then "0: 49 02 …", "1: …" with the pieces in order.
     */
    fun replies(raw: String): List<IntArray> {
        val out = mutableListOf<IntArray>()
        var multi: MutableList<Int>? = null
        for (line in lines(raw)) {
            val t = line.replace(" ", "").uppercase()
            val m = Regex("^([0-9A-F]):([0-9A-F]*)$").find(t)
            if (m != null) {
                if (m.groupValues[1] == "0") {
                    multi?.let { out += it.toIntArray() }
                    multi = mutableListOf()
                }
                multi?.addAll(hexBytes(m.groupValues[2]))
                continue
            }
            multi?.let { out += it.toIntArray() }
            multi = null
            if (t.isEmpty() || t.length % 2 == 1) continue // nothing, or a byte-count line like "014"
            if (t.all { it in "0123456789ABCDEF" }) out += hexBytes(t).toIntArray()
        }
        multi?.let { out += it.toIntArray() }
        return out
    }

    fun isError(raw: String) = lines(raw).any { line -> line == "?" || errors.any { line.contains(it) } }

    /** The data bytes of a mode 01 reply for [pid], or null when the car didn't answer it. */
    fun mode01(raw: String, pid: Int): IntArray? {
        val want = "41%02X".format(pid)
        for (line in lines(raw)) {
            val hex = line.replace(" ", "").uppercase()
            if (!hex.startsWith(want)) continue
            val data = hex.substring(want.length)
            if (data.isEmpty() || data.length % 2 != 0 || !data.all { it in "0123456789ABCDEF" }) continue
            return data.chunked(2).map { it.toInt(16) }.toIntArray()
        }
        return null
    }

    /** Which PIDs the car supports, from a reply to 0100, 0120 or 0140 (a 32-bit mask). */
    fun supported(raw: String, base: Int): Set<Int> {
        val bytes = mode01(raw, base) ?: return emptySet()
        if (bytes.size < 4) return emptySet()
        val out = mutableSetOf<Int>()
        for (i in 0 until 32) {
            if (bytes[i / 8] and (0x80 shr (i % 8)) != 0) out += base + i + 1
        }
        return out
    }

    /** Battery voltage from a reply to ATRV, e.g. "12.6V". */
    fun volts(raw: String): Float? =
        Regex("""(\d{1,2}(?:\.\d+)?)\s*V""").find(raw)?.groupValues?.get(1)?.toFloatOrNull()
}

/** The adapter is fine but the car doesn't answer: it's off or asleep. Asking again straight away would keep it awake. */
class CarAsleepException(message: String) : IOException(message)

/** A link to an ELM327 adapter: sends a command, reads the reply up to the '>' prompt. */
interface ObdTransport : Closeable {
    fun send(text: String)
    fun readReply(timeoutMs: Long): String

    /** Throws away anything the adapter sent that nobody asked for, like a reply that came in late. */
    fun drain() {}
}

/** Talks ELM327 over a [ObdTransport]: sets the adapter up, finds what the car supports, reads values. */
class ObdSession(private val link: ObdTransport, private val advanced: () -> Boolean = { false }) {
    var supported: Set<Int> = emptySet()
        private set

    /** What the adapter says it is talking to the car in, e.g. "ISO 15765-4 (CAN 11/500)". */
    var protocolName: String = ""
        private set

    private val lastReply = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun command(cmd: String, timeoutMs: Long = 2_000): String {
        // Only reads get through; see CommandGuard.
        CommandGuard.check(cmd, advanced())
        link.drain()
        link.send(cmd + "\r")
        return link.readReply(timeoutMs).also { lastReply[cmd] = it }
    }

    /** For Setup: which readings the car lists, and the last raw reply to each one. */
    fun report(): String = buildString {
        appendLine("Protocol: " + protocolName.ifEmpty { "unknown" })
        appendLine("Lists fuel level: " + if (Pid.FUEL.code in supported) "yes" else "no")
        for ((cmd, reply) in lastReply.toSortedMap()) {
            if (cmd.startsWith("01")) appendLine("$cmd: " + Elm.lines(reply).joinToString(" ").ifEmpty { "no reply" })
        }
    }

    /** Resets the adapter and connects to the car. Returns the adapter's name, e.g. "ELM327 v1.5". */
    fun start(): String {
        val id = Elm.lines(command("ATZ", 4_000)).lastOrNull { it.contains("ELM", ignoreCase = true) } ?: "ELM327"
        // No echo, spaces or headers; any protocol. ATAT2 lets the adapter shorten its wait when the car answers quickly.
        for (setup in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATAT2")) command(setup)
        // The first query makes the adapter search for the car's protocol, which can take a while.
        val first = command("0100", 15_000)
        if (Elm.mode01(first, 0x00) == null) {
            throw CarAsleepException("Car didn't answer (${Elm.lines(first).joinToString(" ").ifEmpty { "no reply" }}). Is the ignition on?")
        }
        supported = Elm.supported(first, 0x00)
        if (0x20 in supported) supported = supported + Elm.supported(command("0120"), 0x20)
        if (0x40 in supported) supported = supported + Elm.supported(command("0140"), 0x40)
        protocolName = protocol()
        return id
    }

    fun supports(pid: Pid) = pid.code in supported

    /** The value, or null if the car doesn't support it or didn't answer this time. */
    fun read(pid: Pid): Float? {
        if (!supports(pid)) return null
        return Elm.mode01(command(pid.command), pid.code)?.let { pid.value(it) }
    }

    fun volts(): Float? = Elm.volts(command("ATRV"))

    private fun protocol(): String =
        Elm.lines(command("ATDP")).lastOrNull()?.removePrefix("AUTO, ")?.trim() ?: "unknown"

    /** The check-engine lamp and how many codes are stored, from reading 01 01. */
    fun milStatus(): Pair<Boolean, Int>? {
        val d = Elm.mode01(command("0101"), 0x01) ?: return null
        if (d.isEmpty()) return null
        return (d[0] and 0x80 != 0) to (d[0] and 0x7F)
    }

    /** Fault codes: stored (03), pending (07) and permanent (0A). Each can take a second or two. */
    fun readDtcReport(): DtcReport {
        val mil = milStatus()
        val can = protocolName.contains("CAN", ignoreCase = true)
        fun fetch(service: Int) = Dtc.parse(command("%02X".format(service - 0x40), 5_000), service, hasCount = can)
        return DtcReport(mil?.first, mil?.second, fetch(0x43), fetch(0x47), fetch(0x4A), System.currentTimeMillis())
    }

    fun vin(): String? = Dtc.vin(command("0902", 5_000))

    /**
     * A one-off look at a few manufacturer-specific readings people have used on other Mazdas
     * (fuel level and tire pressures, asked of the instrument cluster). It only asks; it changes
     * nothing in the car. The raw replies are shown in Settings so they can be matched to what the
     * car's own screen says.
     */
    fun probe(): List<String> {
        val out = mutableListOf<String>()
        command("ATSH720")
        for (req in listOf("2261B1", "222A05", "222A06", "222A07", "222A08", "22C901")) {
            out += "720 $req: " + Elm.lines(command(req)).joinToString(" ").ifEmpty { "no reply" }
        }
        command("ATSH7DF") // back to the normal request address
        return out
    }
}
