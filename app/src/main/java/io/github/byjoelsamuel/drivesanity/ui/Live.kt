package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

/**
 * A beat ten times a second, for the parts of the phone screens that show values read straight from the
 * engine (the recording timer, the adapter's status, what's playing).
 *
 * Those parts used to be handed a "tick" number and never read it. Compose skips redrawing a part whose
 * inputs it uses haven't changed, and an input that's never read doesn't count, so they quietly stopped
 * updating. Reading [beat] is what subscribes a part to the beat.
 */
object Live {
    var tick by mutableLongStateOf(0L)
        internal set

    /** Call at the top of a composable that shows live values: it then redraws with every beat. */
    fun beat(): Long = tick
}
