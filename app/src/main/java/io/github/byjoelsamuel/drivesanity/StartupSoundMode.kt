package io.github.byjoelsamuel.drivesanity

/** What plays when the app starts. */
enum class StartupSoundMode(val title: String) {
    OFF("Off"),
    CHIME("Chime"),
    VOICE("Voice"),
    CUSTOM("My file"),
}
