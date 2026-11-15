package io.github.byjoelsamuel.drivesanity.car

import android.content.Context
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.graphics.drawable.IconCompat
import io.github.byjoelsamuel.drivesanity.R

/**
 * Previous, play or pause and next as Android Auto's own buttons, kept on screen beside the picture.
 *
 * Buttons the app draws in its picture can only be pressed on a touch screen: Android Auto doesn't pass a
 * knob's turns and presses to the picture, only to its own buttons. So on a car with just a knob, these are
 * the ones that work, and unlike the strip that appears when the knob is turned, they stay put.
 *
 * The three tiles keep the same titles whatever is playing, and the same on every screen that uses them.
 * Android Auto counts a change of titles as a new screen and only allows a few of those in a row; a changed
 * picture on a tile is just a refresh.
 */
object MusicPanel {
    /** Needs a recent Android Auto (car API level 7). Older ones get the buttons that appear on a knob turn. */
    fun supported(carApiLevel: Int) = carApiLevel >= CarAppApiLevels.LEVEL_7

    fun template(
        ctx: Context,
        strip: ActionStrip,
        playing: Boolean,
        onPrevious: () -> Unit,
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
    ): Template {
        fun tile(title: String, icon: Int, press: () -> Unit) = GridItem.Builder()
            .setTitle(title)
            .setImage(CarIcon.Builder(IconCompat.createWithResource(ctx, icon)).build(), GridItem.IMAGE_TYPE_ICON)
            .setOnClickListener(press)
            .build()
        val tiles = ItemList.Builder()
            .addItem(tile(PREVIOUS, R.drawable.ic_skip_previous, onPrevious))
            .addItem(tile(PLAY_PAUSE, if (playing) R.drawable.ic_pause else R.drawable.ic_play, onPlayPause))
            .addItem(tile(NEXT, R.drawable.ic_skip_next, onNext))
            .build()
        return MapWithContentTemplate.Builder()
            .setContentTemplate(GridTemplate.Builder().setSingleList(tiles).build())
            .setActionStrip(strip)
            .build()
    }

    const val PREVIOUS = "Previous"
    const val PLAY_PAUSE = "Play / Pause"
    const val NEXT = "Next"
}
