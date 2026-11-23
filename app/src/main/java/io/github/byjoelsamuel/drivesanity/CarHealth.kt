package io.github.byjoelsamuel.drivesanity

import android.content.Context

/**
 * A small record of how the car side is doing, so Settings can say why the car screen isn't showing
 * or what went wrong, instead of leaving it a mystery: when Android Auto last opened the app, how
 * many times, and the last error the car screen hit. Kept on the phone; nothing is sent anywhere.
 */
object CarHealth {
    data class State(val sessions: Int, val lastSessionMs: Long, val lastError: String?, val lastErrorMs: Long)

    private const val FILE = "car_health"
    private var lastSavedKey: String? = null
    private var lastSavedMs = 0L

    /** Android Auto opened the app. */
    fun sessionStarted(ctx: Context, now: Long = System.currentTimeMillis()) {
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        p.edit().putInt("sessions", p.getInt("sessions", 0) + 1).putLong("last_session", now).apply()
    }

    /**
     * The car screen hit an error and carried on. The same error repeating thirty times a second is
     * saved once, then again at most every ten seconds.
     */
    fun error(ctx: Context, where: String, t: Throwable, now: Long = System.currentTimeMillis()) {
        val text = "$where: ${t.javaClass.simpleName}${t.message?.let { " – ${it.take(120)}" } ?: ""}"
        if (text == lastSavedKey && now - lastSavedMs < 10_000) return
        lastSavedKey = text
        lastSavedMs = now
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("error", text).putLong("error_at", now).apply()
    }

    fun read(ctx: Context): State {
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return State(p.getInt("sessions", 0), p.getLong("last_session", 0L), p.getString("error", null), p.getLong("error_at", 0L))
    }

    fun clearError(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove("error").remove("error_at").apply()
        lastSavedKey = null
    }

    /** "just now", "5 minutes ago", "3 hours ago", "2 days ago". */
    fun ago(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val s = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        fun n(v: Long, unit: String) = "$v $unit${if (v == 1L) "" else "s"} ago"
        return when {
            s < 60 -> "just now"
            s < 3600 -> n(s / 60, "minute")
            s < 86400 -> n(s / 3600, "hour")
            else -> n(s / 86400, "day")
        }
    }
}
