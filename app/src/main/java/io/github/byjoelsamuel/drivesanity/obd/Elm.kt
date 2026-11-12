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
    FUEL(0x2F, 1, { it[0] * 100f / 255f });             // % of the tank

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

/** A link to an ELM327 adapter: sends a command, reads the reply up to the '>' prompt. */
interface ObdTransport : Closeable {
    fun send(text: String)
    fun readReply(timeoutMs: Long): String

    /** Throws away anything the adapter sent that nobody asked for, like a reply that came in late. */
    fun drain() {}
}

/** Talks ELM327 over a [ObdTransport]: sets the adapter up, finds what the car supports, reads values. */
class ObdSession(private val link: ObdTransport) {
    var supported: Set<Int> = emptySet()
        private set

    private val lastReply = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun command(cmd: String, timeoutMs: Long = 2_000): String {
        link.drain()
        link.send(cmd + "\r")
        return link.readReply(timeoutMs).also { lastReply[cmd] = it }
    }

    /** For Setup: which readings the car lists, and the last raw reply to each one. */
    fun report(): String = buildString {
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
            throw IOException("Car didn't answer (${Elm.lines(first).joinToString(" ").ifEmpty { "no reply" }}). Is the ignition on?")
        }
        supported = Elm.supported(first, 0x00)
        if (0x20 in supported) supported = supported + Elm.supported(command("0120"), 0x20)
        if (0x40 in supported) supported = supported + Elm.supported(command("0140"), 0x40)
        return id
    }

    fun supports(pid: Pid) = pid.code in supported

    /** The value, or null if the car doesn't support it or didn't answer this time. */
    fun read(pid: Pid): Float? {
        if (!supports(pid)) return null
        return Elm.mode01(command(pid.command), pid.code)?.let { pid.value(it) }
    }

    fun volts(): Float? = Elm.volts(command("ATRV"))
}
