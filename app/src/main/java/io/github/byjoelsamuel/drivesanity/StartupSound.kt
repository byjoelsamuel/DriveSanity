package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The start-up sound. By default it's a recording of a Japanese Toyota ETC card reader switching
 * on (res/raw/etc_reader.ogg, by qopsinonstudios on Freesound). Or a short synthesised rising chime
 * in the same spirit (res/raw/etc_chime.wav), optionally followed by the phrase such a reader says,
 * spoken by the phone's own Japanese voice if it has one. Or any sound file the owner picks.
 *
 * It's played as navigation-prompt audio, so it comes out of the car's speakers over Android
 * Auto, and briefly lowers whatever else is playing.
 */
object StartupSound {
    private const val PHRASE = "ETCカードが挿入されました。"
    private var lastMs = 0L

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(context: Context) {
        val app = context.applicationContext
        val mode = Prefs.startupSound(app)
        if (mode == StartupSoundMode.OFF) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastMs < 3_000) return // the phone and the car both start up together
        lastMs = now
        try {
            when (mode) {
                StartupSoundMode.READER -> chime(app, R.raw.etc_reader, speakAfter = false)
                StartupSoundMode.CHIME -> chime(app, R.raw.etc_chime, speakAfter = false)
                StartupSoundMode.VOICE -> chime(app, R.raw.etc_chime, speakAfter = true)
                StartupSoundMode.CUSTOM -> custom(app)
                StartupSoundMode.OFF -> {}
            }
        } catch (_: Exception) {
            // A sound that can't play shouldn't stop the app starting.
        }
    }

    private fun focus(app: Context): AudioFocusRequest {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
        app.getSystemService(AudioManager::class.java).requestAudioFocus(request)
        return request
    }

    private fun release(app: Context, request: AudioFocusRequest) {
        app.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(request)
    }

    private fun chime(app: Context, sound: Int, speakAfter: Boolean) {
        val player = MediaPlayer.create(app, sound, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE) ?: return
        val request = focus(app)
        player.setOnCompletionListener {
            it.release()
            if (speakAfter) speak(app, request) else release(app, request)
        }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            release(app, request)
            true
        }
        player.start()
    }

    private fun speak(app: Context, request: AudioFocusRequest) {
        var tts: TextToSpeech? = null
        fun done() {
            tts?.shutdown()
            release(app, request)
        }
        tts = TextToSpeech(app) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) return@TextToSpeech done()
            val lang = engine.setLanguage(Locale.JAPAN)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return@TextToSpeech done()
            engine.setAudioAttributes(attrs)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = done()

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = done()
            })
            engine.speak(PHRASE, TextToSpeech.QUEUE_FLUSH, null, "etc")
        }
    }

    private fun custom(app: Context) {
        val uri = Prefs.customSound(app)?.let(Uri::parse) ?: return
        val request = focus(app)
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(attrs)
            player.setDataSource(app, uri)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                it.release()
                release(app, request)
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                release(app, request)
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            release(app, request)
            throw e
        }
    }
}
