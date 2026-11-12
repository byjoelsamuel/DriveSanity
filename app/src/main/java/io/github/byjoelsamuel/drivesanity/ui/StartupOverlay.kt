package io.github.byjoelsamuel.drivesanity.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import io.github.byjoelsamuel.drivesanity.StartupPainter

/** The power-on animation over the app when it opens. Tap to skip. */
@Composable
fun StartupOverlay(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val painter = remember { StartupPainter(ctx) }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < StartupPainter.DURATION) {
            t = (withFrameNanos { it } - start) / 1_000_000_000f
        }
        onDone()
    }
    Canvas(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
    ) {
        drawIntoCanvas { painter.draw(it.nativeCanvas, RectF(0f, 0f, size.width, size.height), t) }
    }
}
