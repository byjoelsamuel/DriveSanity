package io.github.byjoelsamuel.drivesanity.obd

/**
 * Everything sent to the adapter goes through here first, so the app can only ever ask the car
 * questions. It never clears fault codes, writes a setting or starts a test: those requests are
 * refused no matter who asks.
 *
 *  - Adapter setup (AT commands) is allowed, except choosing a header, which is advanced.
 *  - Live data (01), freeze frame (02), stored, pending and permanent codes (03, 07, 0A) and the
 *    VIN and car information (09) are allowed.
 *  - Manufacturer-specific reads (22) and choosing a header are only allowed when the person has
 *    turned on advanced reads in Settings.
 *  - Everything else is refused, including clearing codes (04), output tests (08), writes (2E, 2F),
 *    diagnostic sessions (10), resets (11), security (27) and routines (31).
 */
object CommandGuard {
    private val adapter = Regex("""^AT(Z|WS|E[01]|L[01]|S[01]|H[01]|SP[0-9A]|AT[0-2]|RV|DPN?|I|@1|D)$""")
    private val header = Regex("""^ATSH[0-9A-F]{3,8}$""")
    private val hex = Regex("""^[0-9A-F]+$""")
    private val readModes = setOf("01", "02", "03", "07", "09", "0A")

    /** Throws if [command] isn't a read the app is allowed to make. */
    fun check(command: String, advanced: Boolean) {
        val c = command.trim().uppercase().replace(" ", "")
        if (c.isEmpty()) return
        if (c.startsWith("AT")) {
            if (adapter.matches(c)) return
            if (header.matches(c) && advanced) return
            throw IllegalArgumentException("Blocked: $c is not a read-only request")
        }
        if (!hex.matches(c) || c.length < 2) throw IllegalArgumentException("Blocked: $c is not a read-only request")
        val mode = c.substring(0, 2)
        when {
            mode in readModes -> return
            mode == "22" && advanced && c.length == 6 -> return
            else -> throw IllegalArgumentException("Blocked: mode $mode is not a read-only request")
        }
    }

    fun allowed(command: String, advanced: Boolean): Boolean = runCatching { check(command, advanced) }.isSuccess
}
