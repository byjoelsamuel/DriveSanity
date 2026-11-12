package io.github.byjoelsamuel.drivesanity.obd

/** What the car said about its fault codes. */
data class DtcReport(
    /** Is the check-engine lamp on? Null if the car didn't say. */
    val milOn: Boolean?,
    val count: Int?,
    val stored: List<String>,
    val pending: List<String>,
    val permanent: List<String>,
    val readAtMs: Long,
) {
    val all get() = (stored + pending + permanent).distinct()
}

/** Reading fault codes (DTCs) out of the adapter's replies, and saying what they mean. */
object Dtc {
    /** "P0133" from the two bytes 0x01 0x33. */
    fun code(a: Int, b: Int): String {
        val letter = "PCBU"[(a shr 6) and 3]
        return "%c%d%X%02X".format(letter, (a shr 4) and 3, a and 0xF, b)
    }

    /**
     * The codes in a reply to service 03, 07 or 0A. [service] is the reply's first byte (0x43,
     * 0x47 or 0x4A). On CAN the byte after it is the number of codes; other buses have none.
     */
    fun parse(raw: String, service: Int, hasCount: Boolean): List<String> {
        val codes = linkedSetOf<String>()
        for (reply in Elm.replies(raw)) {
            if (reply.isEmpty() || reply[0] != service) continue
            var i = if (hasCount) 2 else 1
            while (i + 1 < reply.size) {
                if (reply[i] != 0 || reply[i + 1] != 0) codes += code(reply[i], reply[i + 1])
                i += 2
            }
        }
        return codes.toList()
    }

    /** The 17-character VIN from a reply to 09 02, or null. */
    fun vin(raw: String): String? {
        for (r in Elm.replies(raw)) {
            if (r.size > 3 && r[0] == 0x49 && r[1] == 0x02) {
                val s = r.drop(3).filter { it in 32..126 }.map { it.toChar() }.joinToString("")
                if (s.length >= 11) return s.take(17)
            }
        }
        return null
    }

    /** A short plain-English meaning: the exact one for common codes, otherwise the system it belongs to. */
    fun describe(code: String): String = KNOWN[code] ?: category(code)

    private fun category(code: String): String {
        if (code.length < 5) return "Fault code"
        val manufacturer = code[1] == '1' || code[1] == '3'
        return when (code[0]) {
            'P' -> if (manufacturer) "Mazda-specific engine or transmission code" else when (code[2]) {
                '0', '1', '2' -> "Fuel and air metering"
                '3' -> "Ignition or misfire"
                '4' -> "Emissions control"
                '5' -> "Speed, idle or auxiliary inputs"
                '6' -> "Computer or output circuit"
                '7', '8', '9' -> "Transmission"
                else -> "Powertrain"
            }
            'C' -> "Chassis: brakes, steering or suspension"
            'B' -> "Body: airbags, doors, comfort"
            else -> "Network: modules not talking"
        }
    }

    private val KNOWN = mapOf(
        "P0016" to "Crank and cam position don't match",
        "P0101" to "Air flow sensor out of range",
        "P0106" to "Manifold pressure sensor out of range",
        "P0113" to "Intake air temperature sensor high",
        "P0117" to "Coolant temperature sensor low",
        "P0118" to "Coolant temperature sensor high",
        "P0121" to "Throttle position out of range",
        "P0128" to "Engine not reaching normal temperature",
        "P0131" to "Oxygen sensor reading low (bank 1)",
        "P0133" to "Oxygen sensor slow (bank 1)",
        "P0135" to "Oxygen sensor heater (bank 1)",
        "P0171" to "Running too lean (bank 1)",
        "P0172" to "Running too rich (bank 1)",
        "P0300" to "Random misfire",
        "P0301" to "Cylinder 1 misfire",
        "P0302" to "Cylinder 2 misfire",
        "P0303" to "Cylinder 3 misfire",
        "P0304" to "Cylinder 4 misfire",
        "P0316" to "Misfire right after starting",
        "P0325" to "Knock sensor circuit",
        "P0335" to "Crankshaft position sensor circuit",
        "P0340" to "Camshaft position sensor circuit",
        "P0401" to "Exhaust gas recirculation flow too low",
        "P0420" to "Catalytic converter below efficiency",
        "P0441" to "Fuel vapour purge flow",
        "P0442" to "Fuel vapour leak (small)",
        "P0446" to "Fuel vapour vent control",
        "P0455" to "Fuel vapour leak (large). Check the fuel cap",
        "P0456" to "Fuel vapour leak (very small)",
        "P0457" to "Fuel vapour leak. Fuel cap loose",
        "P0500" to "Vehicle speed sensor",
        "P0505" to "Idle control system",
        "P0562" to "System voltage low",
        "P0563" to "System voltage high",
        "P0606" to "Engine computer fault",
        "P0700" to "Transmission control fault",
        "P0741" to "Torque converter clutch performance",
        "U0100" to "Lost contact with the engine computer",
    )
}
