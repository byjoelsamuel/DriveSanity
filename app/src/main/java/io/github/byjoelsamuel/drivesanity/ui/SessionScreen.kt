package io.github.byjoelsamuel.drivesanity.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.Session
import io.github.byjoelsamuel.drivesanity.SessionPoint
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.hms
import io.github.byjoelsamuel.drivesanity.oneDecimal
import io.github.byjoelsamuel.drivesanity.track.lapText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One recording opened up: the numbers, the path on a plain map coloured by speed, speed and RPM
 * against time with a scrubber (drag to move along the drive, and the dot on the path moves with
 * it), and the laps if there were any.
 */
@Composable
fun SessionScreen(file: File, units: Units, onBack: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    BackHandler(onBack = onBack)
    val session by produceState<Session?>(null, file) { value = withContext(Dispatchers.IO) { Session.read(file) } }
    var at by remember { mutableFloatStateOf(0f) } // 0..1 along the drive

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Trips", color = Ui.accent) }
        }
        Text(prettyName(file), style = MaterialTheme.typography.titleLarge, color = Ui.fg)
        val s = session
        if (s == null) {
            Text("Reading…", style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        if (s.points.isEmpty()) {
            Text("There's nothing in this recording.", style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.padding(top = 12.dp))
            Row { TextButton(onClick = onDelete) { Text("Delete", color = Ui.clay) } }
            return@Column
        }

        SectionTitle("The drive")
        InfoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Distance", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                    BigValue(oneDecimal(units.distance(s.distanceM)), units.distanceLabel)
                }
                Column {
                    Text("Time", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                    BigValue(hms(s.durationS), "")
                }
            }
            Spacer(Modifier.height(12.dp))
            Stat("Top speed", "${units.speed(s.topKmh / 3.6f).roundToInt()} ${units.label}")
            Stat("Average while moving", "${units.speed(s.avgMovingKmh / 3.6f).roundToInt()} ${units.label}")
            Stat("Highest revs", if (s.maxRpm > 0) "%,d".format(s.maxRpm.roundToInt()) else "–")
            Stat("Hardest braking", "%.2f g".format(s.peakBrakeG))
            Stat("Hardest acceleration", "%.2f g".format(s.peakAccelG))
            Stat("Hardest corner", "%.2f g".format(s.peakLatG))
        }

        if (s.path.size > 1) {
            SectionTitle("Path")
            InfoCard {
                PathMap(s, at)
                Text("Coloured by speed: slow is green, fast is red.", style = MaterialTheme.typography.bodySmall, color = Ui.dim, modifier = Modifier.padding(top = 6.dp))
            }
        }

        SectionTitle("Speed and revs")
        InfoCard {
            Chart(s, at) { at = it }
            Spacer(Modifier.height(8.dp))
            val p = pointAt(s, at)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Readout("Time", hms(p.t))
                Readout("Speed", p.kmh?.let { "${units.speed(it / 3.6f).roundToInt()} ${units.label}" } ?: "–")
                Readout("RPM", p.rpm?.let { "%,d".format(it.roundToInt()) } ?: "–")
                Readout("Throttle", p.throttle?.let { "${it.roundToInt()}%" } ?: "–")
            }
            Text("Drag along the graph to move through the drive.", style = MaterialTheme.typography.bodySmall, color = Ui.dim, modifier = Modifier.padding(top = 6.dp))
        }

        if (s.laps.isNotEmpty()) {
            SectionTitle("Laps")
            InfoCard {
                val best = s.bestLap
                Text(s.laps.first().track, style = MaterialTheme.typography.labelLarge, color = Ui.dim)
                for (l in s.laps) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("Lap ${l.number}", style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.weight(1f))
                        Text(lapText(l.ms) + if (l === best) "  best" else "", style = MaterialTheme.typography.bodyMedium, color = if (l === best) Ui.good else Ui.fg)
                    }
                }
            }
        }

        Row(Modifier.padding(top = 16.dp)) {
            TextButton(onClick = onShare) { Text("Share spreadsheet") }
            TextButton(onClick = onDelete) { Text("Delete", color = Ui.clay) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun pointAt(s: Session, at: Float): SessionPoint {
    val i = (at * (s.points.size - 1)).roundToInt().coerceIn(0, s.points.size - 1)
    return s.points[i]
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ui.fg)
    }
}

@Composable
private fun Readout(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ui.dim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ui.fg)
    }
}

private fun speedColor(f: Float): Color = when {
    f < 0.35f -> Ui.good
    f < 0.65f -> Ui.sand
    f < 0.85f -> Ui.clay
    else -> Ui.red
}

/** The recorded path, fitted into the box with the right proportions, coloured by speed; the dot is where the scrubber is. */
@Composable
private fun PathMap(s: Session, at: Float) {
    val path = s.path
    val top = max(1f, s.topKmh)
    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val lat0 = path.map { it.lat!! }.average()
        val mLon = cos(Math.toRadians(lat0))
        val xs = path.map { it.lon!! * mLon }
        val ys = path.map { it.lat!! }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val spanX = max(maxX - minX, 1e-6)
        val spanY = max(maxY - minY, 1e-6)
        val pad = 14.dp.toPx()
        val k = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY).toFloat()
        val offX = (size.width - (spanX * k).toFloat()) / 2
        val offY = (size.height - (spanY * k).toFloat()) / 2
        fun pt(i: Int) = Offset(offX + ((xs[i] - minX) * k).toFloat(), size.height - offY - ((ys[i] - minY) * k).toFloat())
        for (i in 1 until path.size) {
            if (path[i].t - path[i - 1].t > 30.0) continue
            drawLine(speedColor(((path[i].kmh ?: 0f) / top)), pt(i - 1), pt(i), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Ui.good, 5.dp.toPx(), pt(0))
        drawCircle(Ui.red, 5.dp.toPx(), pt(path.size - 1))
        val here = pointAt(s, at)
        val idx = path.indexOfFirst { it.t >= here.t }.takeIf { it >= 0 } ?: (path.size - 1)
        drawCircle(Ui.fg, 6.dp.toPx(), pt(idx))
    }
}

/** Speed (bright) and revs (faint) against time. Drag or tap to move the scrubber. */
@Composable
private fun Chart(s: Session, at: Float, onAt: (Float) -> Unit) {
    val topKmh = max(1f, s.topKmh)
    val topRpm = max(1f, s.maxRpm)
    Canvas(
        Modifier.fillMaxWidth().height(150.dp)
            .pointerInput(s) { detectTapGestures { onAt((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(s) { detectDragGestures { change, _ -> onAt((change.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val n = s.points.size
        if (n < 2) return@Canvas
        fun x(i: Int) = size.width * i / (n - 1)
        for (q in 1..3) drawLine(Ui.line, Offset(0f, size.height * q / 4f), Offset(size.width, size.height * q / 4f), strokeWidth = 1.dp.toPx())
        for (i in 1 until n) {
            s.points[i].rpm?.let { r ->
                val p = s.points[i - 1].rpm ?: r
                drawLine(Ui.clay.copy(alpha = 0.45f), Offset(x(i - 1), size.height * (1 - p / topRpm)), Offset(x(i), size.height * (1 - r / topRpm)), strokeWidth = 2.dp.toPx())
            }
        }
        for (i in 1 until n) {
            s.points[i].kmh?.let { v ->
                val p = s.points[i - 1].kmh ?: v
                drawLine(Ui.accent, Offset(x(i - 1), size.height * (1 - p / topKmh)), Offset(x(i), size.height * (1 - v / topKmh)), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        val sx = size.width * at
        drawLine(Ui.fg, Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.5.dp.toPx())
    }
}
