package io.github.byjoelsamuel.drivesanity.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.byjoelsamuel.drivesanity.BrakeLevel
import io.github.byjoelsamuel.drivesanity.Colors
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Revs
import io.github.byjoelsamuel.drivesanity.Snapshot
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.map.MapPainter
import io.github.byjoelsamuel.drivesanity.map.MapView
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.SpeedLimit
import io.github.byjoelsamuel.drivesanity.oneDecimal
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DriveTab(s: Snapshot, units: Units, tick: Long, actions: PhoneActions, openSetup: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("DriveSanity", style = MaterialTheme.typography.titleLarge, color = Ui.fg)
            Spacer(Modifier.height(10.dp))
            StatusRow(s, actions, openSetup)

            if (!s.calibrated || s.calibrating) {
                Spacer(Modifier.height(12.dp))
                CalibrateCallout(s, actions)
            }
            Spacer(Modifier.height(12.dp))
            SpeedCard(s, units, actions)
            Spacer(Modifier.height(12.dp))
            NavCard(units, tick, actions)

            SectionTitle("Driving")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrakeCard(s, Modifier.weight(1f))
                GForceCard(s, Modifier.weight(1f))
            }

            SectionTitle("Engine")
            if (s.obdLive) EngineCards(s, units) else ConnectObdCard(actions)
            Spacer(Modifier.height(100.dp)) // room for the record button
        }
        RecordButton(
            Modifier.align(Alignment.BottomCenter).padding(16.dp),
            tick,
            actions,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(s: Snapshot, actions: PhoneActions, openSetup: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !s.gpsOn -> StatusPill("No location access", Ui.red) { actions.askLocation() }
            s.accuracyM == null -> StatusPill("Finding GPS…", Ui.sand)
            else -> StatusPill("GPS ±${s.accuracyM.roundToInt()} m", Ui.good)
        }
        if (s.obdLive) StatusPill("OBD2 connected", Ui.good) else StatusPill("OBD2 not connected", Ui.dim) { actions.pickObd() }
        when {
            s.calibrating -> StatusPill("Calibrating…", Ui.sand)
            s.calibrated -> StatusPill("Calibrated", Ui.good, openSetup)
            else -> StatusPill("Not calibrated", Ui.sand, openSetup)
        }
    }
}

@Composable
private fun CalibrateCallout(s: Snapshot, actions: PhoneActions) {
    InfoCard(title = "One-time setup") {
        Text(
            if (s.calibrating) "Hold still for a second…"
            else "Put your phone in its mount, stop the car and tap Calibrate. The app learns which way is forward, so braking and turns read correctly.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ui.fg,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = actions::calibrate, enabled = !s.calibrating) { Text("Calibrate") }
    }
}

@Composable
private fun SpeedCard(s: Snapshot, units: Units, actions: PhoneActions) {
    val limit = SpeedLimit.current
    val speed = s.speedMps?.let { units.speed(it).roundToInt() }
    val limitInUnits = limit?.let { if (units == Units.MPH) it.inMph() else it.inKmh() }
    val over = speed != null && limitInUnits != null && speed > limitInUnits + (if (units == Units.MPH) 5 else 8)
    InfoCard(onClick = actions::swapUnits) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (speed == null) {
                    Text(
                        if (s.gpsOn) "Waiting for GPS…" else "Location is off",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ui.dim,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        speed.toString(),
                        fontSize = 84.sp,
                        lineHeight = 84.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (over) Ui.clay else Ui.fg,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(units.label, style = MaterialTheme.typography.titleMedium, color = Ui.dim, modifier = Modifier.padding(bottom = 14.dp))
                }
                val other = units.other
                Text(
                    "${s.speedMps?.let { other.speed(it).roundToInt() } ?: "–"} ${other.label}  ·  tap to swap",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ui.dim,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallStat("Gear", s.gear?.toString() ?: "–")
                if (limitInUnits != null) SmallStat("Limit", limitInUnits.toString(), if (over) Ui.clay else Ui.fg)
            }
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, color: Color = Ui.fg) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ui.dim)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
@Suppress("UNUSED_PARAMETER") // tick: refreshes with the live route
private fun NavCard(units: Units, tick: Long, actions: PhoneActions) {
    val route = Nav.route
    val msg = Nav.message
    if (!Nav.active && msg == null) {
        OutlinedButton(onClick = actions::navigate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Where to?", style = MaterialTheme.typography.titleSmall, color = Ui.dim, modifier = Modifier.weight(1f))
            Text("Navigate", style = MaterialTheme.typography.labelLarge, color = Ui.accent)
        }
        return
    }
    InfoCard(title = "Navigation") {
        if (route != null && route.steps.isNotEmpty()) {
            val step = route.steps[Nav.next]
            Text(MapPainter.distance(Nav.toNextM, units), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ui.fg)
            Text(step.text, style = MaterialTheme.typography.bodyLarge, color = Ui.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                "${MapPainter.distance(Nav.remainingM, units)} left · ${(Nav.remainingS / 60).roundToInt()} min · arrive ${Nav.arrivalTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
            Spacer(Modifier.height(12.dp))
            AndroidView(
                factory = { MapView(it) },
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Text(msg ?: "", style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
        }
        if (Nav.active) {
            TextButton(onClick = actions::endRoute) { Text("End route", color = Ui.clay) }
        }
    }
}

private fun brakeWords(level: BrakeLevel) = when (level) {
    BrakeLevel.NONE -> "Not braking"
    BrakeLevel.LIGHT -> "Light"
    BrakeLevel.MODERATE -> "Moderate"
    BrakeLevel.HARD -> "Hard"
    BrakeLevel.VERY_HARD -> "Very hard"
}

@Composable
private fun BrakeCard(s: Snapshot, modifier: Modifier) {
    val brake = s.g?.brake ?: 0f
    val level = BrakeLevel.of(brake)
    InfoCard(modifier, title = "Braking") {
        Text(if (s.g == null) "–" else brakeWords(level), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = if (level == BrakeLevel.NONE) Ui.fg else Color(Colors.brake(level)))
        Text(
            when {
                !s.calibrated -> "Calibrate to see"
                s.g == null -> "Waiting for the motion sensor"
                else -> "%.2f g".format(brake)
            },
            style = MaterialTheme.typography.bodySmall,
            color = Ui.dim,
        )
        Spacer(Modifier.height(10.dp))
        Bar(brake / 0.8f, Color(Colors.brake(level)))
    }
}

@Composable
private fun GForceCard(s: Snapshot, modifier: Modifier) {
    InfoCard(modifier, title = "G-force") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.g?.let { "%.2f".format(it.total) } ?: "–", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ui.fg, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(56.dp)) {
                val r = size.minDimension / 2
                val c = center
                drawCircle(Ui.line, r, c, style = Stroke(2f))
                drawCircle(Ui.line, r / 2, c, style = Stroke(2f))
                drawLine(Ui.line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2f)
                drawLine(Ui.line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f)
                val g = s.g ?: return@Canvas
                val k = 1.2f / maxOf(g.total, 1.2f)
                drawCircle(Ui.accent, r * 0.16f, Offset(c.x + g.lat * k * r, c.y - g.lon * k * r))
            }
        }
        Text("Up = speeding up, down = braking", style = MaterialTheme.typography.bodySmall, color = Ui.dim)
    }
}

@Composable
private fun ConnectObdCard(actions: PhoneActions) {
    InfoCard {
        Text("See RPM, fuel and engine temperatures", style = MaterialTheme.typography.titleSmall, color = Ui.fg)
        Spacer(Modifier.height(4.dp))
        Text(
            "Plug a Bluetooth OBD2 adapter into the port under the dashboard, turn the car on, then connect it here. You only do this once.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ui.dim,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = actions::pickObd) { Text("Connect adapter") }
    }
}

@Composable
private fun EngineCards(s: Snapshot, units: Units) {
    fun temp(c: Float?) = c?.let { if (units == Units.MPH) (it * 9 / 5 + 32).roundToInt() else it.roundToInt() }
    val tUnit = if (units == Units.MPH) "°F" else "°C"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(title = "RPM") {
            BigValue(s.rpm?.let { "%,d".format((it / 50f).roundToInt() * 50) } ?: "–", "", s.rpm?.let { Color(Colors.rpm(it)) } ?: Ui.dim)
            Spacer(Modifier.height(10.dp))
            RpmBar(s.rpm)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val fuel = s.fuelPct
            InfoCard(Modifier.weight(1f), title = "Fuel") {
                BigValue(fuel?.roundToInt()?.toString() ?: "–", "%")
                Text(
                    when {
                        !s.fuelSupported -> "Your car doesn't report it"
                        fuel == null -> "Waiting…"
                        fuel < 12.5f -> "Fill up soon"
                        fuel < 25f -> "Under a quarter"
                        else -> "OK"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Ui.dim,
                )
                Spacer(Modifier.height(8.dp))
                Bar((fuel ?: 0f) / 100f, if ((fuel ?: 100f) < 25f) Ui.sand else Ui.fg)
            }
            val c = s.coolantC
            InfoCard(Modifier.weight(1f), title = "Coolant") {
                BigValue(temp(c)?.toString() ?: "–", tUnit, if (c != null && c > 105f) Ui.red else Ui.fg)
                Text(
                    when {
                        c == null -> "Waiting…"
                        c < 60f -> "Warming up, go easy"
                        c > 105f -> "Running hot"
                        else -> "Normal"
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (c != null && c < 60f) Ui.accent else Ui.dim,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val v = s.volts
            InfoCard(Modifier.weight(1f), title = "Battery") {
                BigValue(v?.let { "%.1f".format(it) } ?: "–", "V")
                Text(
                    when {
                        v == null -> "Waiting…"
                        v > 15.2f -> "Too high"
                        v >= 13.2f -> "Charging"
                        v >= 12.2f -> "OK"
                        else -> "Low"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Ui.dim,
                )
            }
            InfoCard(Modifier.weight(1f), title = "Intake air") {
                BigValue(temp(s.intakeC)?.toString() ?: "–", tUnit)
                Text("Air going into the engine", style = MaterialTheme.typography.bodySmall, color = Ui.dim)
            }
        }
        InfoCard(title = "Trip") {
            BigValue(oneDecimal(units.distance(s.tripM)), units.distanceLabel)
        }
    }
}

@Composable
private fun RpmBar(rpm: Float?) {
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val n = (Revs.MAX / 250f).toInt()
        val step = size.width / n
        for (i in 0 until n) {
            val from = i * 250f
            val color = when {
                rpm != null && rpm > from -> Color(Colors.rpm(from + 125f))
                from + 250f > Revs.REDLINE -> Color(Colors.RED_DIM)
                else -> Ui.line
            }
            drawRect(color, Offset(i * step + step * 0.12f, 0f), androidx.compose.ui.geometry.Size(step * 0.76f, size.height))
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER") // tick: keeps the timer running
private fun RecordButton(modifier: Modifier, tick: Long, actions: PhoneActions) {
    val since = DriveEngine.recordingSinceMs
    val rec = since != null
    Button(
        onClick = actions::toggleRecording,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (rec) Ui.red else Ui.raised,
            contentColor = Ui.fg,
        ),
    ) {
        if (rec) {
            val sec = (SystemClock.elapsedRealtime() - since!!) / 1000
            Text("Stop recording  ·  %d:%02d".format(sec / 60, sec % 60), style = MaterialTheme.typography.titleSmall)
        } else {
            Canvas(Modifier.size(12.dp)) { drawCircle(Ui.red, min(size.width, size.height) / 2) }
            Spacer(Modifier.width(10.dp))
            Text("Start recording", style = MaterialTheme.typography.titleSmall)
        }
    }
}
