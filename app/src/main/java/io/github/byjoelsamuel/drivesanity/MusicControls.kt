package io.github.byjoelsamuel.drivesanity

import android.content.Context
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher

/** What each button on the Music screen does. */
object MusicControls {
    fun press(ctx: Context, c: MusicPainter.Control) {
        when (c) {
            MusicPainter.Control.PREV -> MediaWatcher.previous()
            MusicPainter.Control.PLAY -> MediaWatcher.playPause()
            MusicPainter.Control.NEXT -> MediaWatcher.next()
            MusicPainter.Control.SWAP -> Prefs.setMusicLeft(ctx, !Prefs.musicLeft(ctx))
        }
    }
}
