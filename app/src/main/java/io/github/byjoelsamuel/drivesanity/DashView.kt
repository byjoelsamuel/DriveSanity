package io.github.byjoelsamuel.drivesanity

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View

/** The car dashboard drawn in a phone view: the preview of the Android Auto screen in Setup. */
class DashView(context: Context) : View(context) {
    private val painter = DashPainter(context)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        painter.draw(canvas, rect, DriveEngine.snapshot(), Prefs.units(context), Prefs.layout(context))
        postInvalidateDelayed(33)
    }
}
