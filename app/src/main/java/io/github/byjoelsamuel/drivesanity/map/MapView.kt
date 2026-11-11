package io.github.byjoelsamuel.drivesanity.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Fonts
import io.github.byjoelsamuel.drivesanity.Prefs

/** The live map on its own, for the phone's Drive tab while navigating. Redraws ~30 times a second. */
class MapView(context: Context) : View(context) {
    private val painter = MapPainter(Fonts.medium(context), Fonts.semibold(context))
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        painter.draw(canvas, rect, DriveEngine.snapshot(), Prefs.units(context))
        postInvalidateDelayed(33)
    }
}
