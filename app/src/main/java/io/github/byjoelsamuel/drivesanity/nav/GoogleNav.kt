package io.github.byjoelsamuel.drivesanity.nav

import android.app.Notification
import android.os.SystemClock
import android.service.notification.StatusBarNotification

/**
 * The directions Google Maps is giving on the phone, read from the notification it keeps up while it
 * navigates: the next turn and how far away it is, and the time left, distance left and arrival time.
 * It's only the text of that one notification, so there's no screen recording and no Google account or
 * key involved, and the arrival time and minutes left are Google's own figures (which include traffic).
 * Google doesn't publish what the notification looks like, so this reads it loosely and shows nothing if it
 * can't make sense of it.
 */
object GoogleNav {
    const val PKG = "com.google.android.apps.maps"
    private const val STALE_MS = 3 * 60_000L

    /** What the next-turn box shows. Any part may be missing. */
    data class Info(
        val instruction: String,
        /** How far to the next turn, as Google wrote it ("0.3 mi", "250 ft"). */
        val toTurn: String?,
        val eta: String?,
        val timeLeft: String?,
        val distanceLeft: String?,
        val arrow: Arrow,
        val seenMs: Long = SystemClock.elapsedRealtime(),
    ) {
        /** "5.2 mi · 12 min · ETA 8:49 PM", with whatever of those there is. */
        val trip: String
            get() = listOfNotNull(distanceLeft, timeLeft, eta?.let { "ETA $it" }).joinToString(" · ")
    }

    @Volatile var info: Info? = null
        private set

    /** Whether the person wants Google's directions shown. Set from the saved setting. */
    @Volatile var enabled = true

    /** The directions to show now, or null when Google Maps isn't navigating (or hasn't said anything for a while). */
    fun current(): Info? {
        val i = info ?: return null
        if (!enabled) return null
        return if (SystemClock.elapsedRealtime() - i.seenMs > STALE_MS) null else i
    }

    /** Looks through what's on the notification shade for Google Maps' navigation notification. */
    fun refresh(active: Array<StatusBarNotification>?) {
        info = active
            ?.filter { it.packageName == PKG && it.notification != null }
            ?.sortedByDescending { it.isOngoing }
            ?.firstNotNullOfOrNull { fromNotification(it.notification) }
    }

    fun clear() {
        info = null
    }

    /** What a notification says, or null if it isn't directions. */
    fun fromNotification(n: Notification): Info? {
        val e = n.extras ?: return null
        val lines = buildList {
            for (key in listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT)) {
                e.getCharSequence(key)?.toString()?.let { add(it) }
            }
            e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.lines()?.let { addAll(it) }
        }
        return parse(lines)
    }

    private val DISTANCE = Regex("""\d+(?:[.,]\d+)?\s?(?:mi|miles?|km|kilomet(?:er|re)s?|ft|feet|foot|m|met(?:er|re)s?|yd|yards?)\b""", RegexOption.IGNORE_CASE)
    private val DURATION = Regex("""(?:\d+\s?(?:h|hr|hrs|hours?)\s?)?\d+\s?(?:min|mins|minutes?)\b|\d+\s?(?:h|hr|hrs|hours?)\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b\d{1,2}:\d{2}(?:\s?[AaPp]\.?[Mm]\.?)?""")

    /** The directions in a few lines of notification text, or null if they don't look like directions. */
    fun parse(rawLines: List<String>): Info? {
        // Split "12 min (5.2 mi) · ETA 8:49 PM" into its pieces.
        val pieces = rawLines
            .flatMap { it.split('·', '•', '|', '\n') }
            .map { it.trim().trim('(', ')', ',', '–', '—').trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (pieces.isEmpty()) return null

        var eta: String? = null
        var timeLeft: String? = null
        var distanceLeft: String? = null
        val bareDistances = ArrayList<String>()
        val words = ArrayList<String>()
        for (piece in pieces) {
            val clock = CLOCK.find(piece)
            val dur = DURATION.find(piece)
            val dist = DISTANCE.find(piece)
            val onlyNumbers = piece.replace(CLOCK, "").replace(DURATION, "").replace(DISTANCE, "")
                .replace(Regex("""(?i)\b(eta|arrive|arriving|arrival|at|by|in|left|remaining|to go)\b"""), "")
                .replace(Regex("""[\s().,:/-]"""), "").isEmpty()
            when {
                clock != null && onlyNumbers -> eta = eta ?: clock.value.trim()
                dur != null && onlyNumbers -> {
                    timeLeft = timeLeft ?: dur.value.trim()
                    // "12 min (5.2 mi)" gives the distance left alongside the time left.
                    if (dist != null) distanceLeft = distanceLeft ?: dist.value.trim()
                }
                dist != null && onlyNumbers -> bareDistances += dist.value.trim()
                else -> words += piece
            }
        }
        // A bare distance is how far to the next turn; a second one is how far to the end.
        val toTurn = bareDistances.firstOrNull()
        if (distanceLeft == null) distanceLeft = bareDistances.getOrNull(1)
        // The instruction is the wordiest of what's left (a "250 ft" in front of it is split off).
        var instruction = words.maxByOrNull { it.length } ?: return null
        var turnFromText = toTurn
        if (turnFromText == null) {
            DISTANCE.find(instruction)?.let { m ->
                if (m.range.first == 0) {
                    turnFromText = m.value.trim()
                    instruction = instruction.removeRange(m.range).trim().trimStart('-', '–', ',', ' ').ifEmpty { instruction }
                }
            }
        }
        if (instruction.length < 3) return null
        return Info(
            instruction = instruction,
            toTurn = turnFromText,
            eta = eta,
            timeLeft = timeLeft,
            distanceLeft = distanceLeft,
            arrow = arrowFor(instruction),
        )
    }

    /** The picture to draw for an instruction, judged from its words. */
    fun arrowFor(text: String): Arrow {
        val t = text.lowercase()
        return when {
            "u-turn" in t || "u turn" in t -> Arrow.UTURN
            "arrive" in t || "destination" in t || "your destination" in t -> Arrow.ARRIVE
            "slight left" in t || "bear left" in t || "keep left" in t -> Arrow.SLIGHT_LEFT
            "slight right" in t || "bear right" in t || "keep right" in t -> Arrow.SLIGHT_RIGHT
            "left" in t -> Arrow.LEFT
            "right" in t -> Arrow.RIGHT
            else -> Arrow.STRAIGHT
        }
    }
}
