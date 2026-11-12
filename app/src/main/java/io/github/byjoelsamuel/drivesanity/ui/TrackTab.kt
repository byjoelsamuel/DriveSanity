package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Snapshot
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.track.Track
import io.github.byjoelsamuel.drivesanity.track.deltaText
import io.github.byjoelsamuel.drivesanity.track.lapText
import kotlin.math.roundToInt

/** Lap timing and the standing-start timer. */
@Composable
@Suppress("UNUSED_PARAMETER") // units: kept for the speeds shown beside the timers
fun TrackTab(s: Snapshot, units: Units, tick: Long) {
    val ctx = LocalContext.current
    val lap = s.lap
    var naming by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val saved = remember(tick / 10, lap == null) { Track.saved(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Track", style = MaterialTheme.typography.titleLarge, color = Ui.fg)

        SectionTitle("Lap timer")
        InfoCard {
            if (lap == null) {
                Text(
                    "Stand or drive at the spot you want as the start/finish line, facing the way you'll cross it, and set the line there. Laps are timed to a few hundredths between GPS fixes, and the drive is recorded at one row a second so the laps are in Trips afterwards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ui.dim,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (DriveEngine.lineHere("x") == null) {
                        note = "The line needs to know which way you're heading. Drive forward a little, then tap again."
                    } else {
                        note = null
                        naming = true
                    }
                }) { Text("Set start/finish line here") }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ui.sand, modifier = Modifier.padding(top = 6.dp)) }
                if (saved.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Saved lines", style = MaterialTheme.typography.labelLarge, color = Ui.dim)
                    for (line in saved) {
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(line.name, style = MaterialTheme.typography.bodyLarge, color = Ui.fg, modifier = Modifier.weight(1f))
                            TextButton(onClick = { DriveEngine.armLaps(ctx, line) }) { Text("Start") }
                            TextButton(onClick = { Track.delete(ctx, line.name); note = null }) { Text("Delete", color = Ui.clay) }
                        }
                    }
                }
            } else {
                Text(lap.trackName, style = MaterialTheme.typography.labelLarge, color = Ui.dim)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text(if (lap.number == 0) "Cross the line to start" else "Lap ${lap.number}", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                        BigValue(if (lap.number == 0) "–" else lapText(lap.currentMs), "", size = 40)
                    }
                    Column {
                        Text("Against best", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                        BigValue(deltaText(lap.deltaMs), "s", color = when { lap.deltaMs == null -> Ui.dim; lap.deltaMs <= 0 -> Ui.good; else -> Ui.clay }, size = 40)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Line("Last lap", lapText(lap.lastMs))
                Line("Best lap", lapText(lap.bestMs))
                Line("Laps done", lap.lapsDone.toString())
                val laps = Track.laps()
                if (laps.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val best = laps.minOf { it.ms }
                    for (l in laps.asReversed()) {
                        Line("Lap ${l.number}", lapText(l.ms) + if (l.ms == best) "  best" else "", if (l.ms == best) Ui.good else Ui.fg)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { DriveEngine.disarmLaps(ctx) }, modifier = Modifier.align(Alignment.End)) { Text("Stop lap timing", color = Ui.clay) }
            }
        }

        SectionTitle("Standing start")
        InfoCard {
            val run = s.run
            Text(
                when {
                    run == null -> "Stop the car, then launch. 0–60 mph, 0–100 km/h and the quarter mile with its trap speed are timed on their own from the moment you move off, and shown here and on the Sport screen."
                    run.running -> "Timing…"
                    run.to60Ms != null || run.to100Ms != null -> "Last run"
                    else -> "Ready. Launch when you like."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.dim,
            )
            if (run != null) {
                Spacer(Modifier.height(10.dp))
                fun sec(ms: Long?) = if (ms == null) "–" else "%.2f s".format(java.util.Locale.US, ms / 1000.0)
                Line("0–60 mph", sec(run.to60Ms))
                Line("0–100 km/h", sec(run.to100Ms))
                Line(
                    "Quarter mile",
                    sec(run.quarterMs) + (run.trapKmh?.let { " at ${units.speed(it / 3.6f).roundToInt()} ${units.label}" } ?: ""),
                )
            }
            Text(
                "Timed from the car's own speed when the OBD2 adapter is connected, otherwise from GPS, which is slower to react. Do it somewhere safe and legal.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (naming) {
        var name by remember { mutableStateOf("Track ${saved.size + 1}") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this line") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val line = DriveEngine.lineHere(name.trim().ifEmpty { "Track" })
                    if (line == null) {
                        note = "Lost the position. Drive forward a little and try again."
                    } else {
                        Track.save(ctx, line)
                        DriveEngine.armLaps(ctx, line)
                    }
                    naming = false
                }) { Text("Save and start") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
            containerColor = Ui.raised,
        )
    }
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color = Ui.fg) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
