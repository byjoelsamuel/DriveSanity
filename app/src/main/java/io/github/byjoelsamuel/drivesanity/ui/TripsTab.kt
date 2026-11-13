package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.produceState
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.hms
import io.github.byjoelsamuel.drivesanity.oneDecimal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
@Suppress("UNUSED_PARAMETER") // tick: the trip numbers are live
fun TripsTab(units: Units, recordings: List<File>, tick: Long, actions: PhoneActions) {
    val st = DriveEngine.stats
    var confirmReset by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<File?>(null) }
    var open by remember { mutableStateOf<File?>(null) }

    // A recording opened up: its path, graphs and laps.
    open?.takeIf { it.exists() }?.let { f ->
        SessionScreen(f, units, onBack = { open = null }, onShare = { actions.share(f) }, onDelete = { deleting = f })
    }
    val showing = open?.takeIf { it.exists() }

    if (showing == null) Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Trips", style = MaterialTheme.typography.titleLarge, color = Ui.fg)

        SectionTitle("This trip")
        InfoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column {
                    Text("Distance", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                    BigValue(oneDecimal(units.distance(st.distanceM)), units.distanceLabel)
                }
                Column {
                    Text("Driving time", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                    BigValue(hms(st.movingSec), "")
                }
            }
            Spacer(Modifier.height(14.dp))
            Line("Average speed", "${units.speed(st.avgMovingSpeedMps).toInt()} ${units.label}")
            Line("Top speed", "${units.speed(st.topSpeedMps).toInt()} ${units.label}")
            Line("Hardest braking", "%.2f g".format(st.peakBrakeG))
            Line("Hardest acceleration", "%.2f g".format(st.peakAccelG))
            Line("Hardest turn", "%.2f g".format(st.peakTurnG))
            Spacer(Modifier.height(10.dp))
            if (DriveEngine.profile.petrol) {
                val avg = DriveEngine.economy.tripLitresPer100Km
                Line("Fuel economy (estimate)", units.economyText(avg, null))
                Line("Fuel used (estimate)", if (avg == null) "–" else if (units == Units.MPH) "%.2f gal".format(DriveEngine.economy.litres / 3.78541) else "%.2f L".format(DriveEngine.economy.litres))
            } else {
                Line("Fuel economy", "Petrol engines only")
            }
            Spacer(Modifier.height(10.dp))
            Line("Hard brakes", st.hardBrakes.count.toString())
            Line("Hard launches", st.hardLaunches.count.toString())
            Line("Hard turns", st.hardTurns.count.toString())
            Text(
                "Counted while moving. \"Hard\" means over 0.4 g braking or turning, or 0.35 g speeding up.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
                modifier = Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.End)) { Text("Reset trip") }
        }

        SectionTitle("Recordings")
        if (recordings.isEmpty()) {
            InfoCard {
                Text(
                    "Nothing recorded yet. Tap Start recording on the Drive tab and the drive is saved here: tap it to see the path and graphs, or share it as a spreadsheet (CSV).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ui.dim,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (f in recordings) {
                RecordingCard(f, units, onOpen = { open = f }, onShare = { actions.share(f) }, onDelete = { deleting = f })
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset this trip?") },
            text = { Text("Distance, times, peaks and counts go back to zero. Recordings are kept.") },
            confirmButton = { TextButton(onClick = { DriveEngine.resetTrip(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            containerColor = Ui.raised,
        )
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this recording?") },
            text = { Text(prettyName(f)) },
            confirmButton = { TextButton(onClick = { actions.delete(f); deleting = null }) { Text("Delete", color = Ui.clay) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            containerColor = Ui.raised,
        )
    }
}

/** One recording in the list: when, how far and how fast; tap it to open it. */
@Composable
private fun RecordingCard(f: File, units: Units, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val session by produceState<Session?>(null, f) { value = withContext(Dispatchers.IO) { Session.read(f) } }
    InfoCard(onClick = onOpen) {
        Text(prettyName(f), style = MaterialTheme.typography.titleSmall, color = Ui.fg)
        val s = session
        Text(
            if (s == null || s.points.isEmpty()) "%.1f KB".format(f.length() / 1000.0)
            else buildString {
                append("${oneDecimal(units.distance(s.distanceM))} ${units.distanceLabel} · ${hms(s.durationS)} · top ${units.speed(s.topKmh / 3.6f).roundToInt()} ${units.label}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = Ui.dim,
        )
        Row(Modifier.align(Alignment.End)) {
            TextButton(onClick = onShare) { Text("Share") }
            TextButton(onClick = onDelete) { Text("Delete", color = Ui.clay) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ui.fg)
    }
}

/** "drive-20261111-193600.csv" → "Wed 11 Nov, 7:36 PM". */
fun prettyName(f: File): String {
    val stamp = f.name.removePrefix("drive-").removeSuffix(".csv")
    return runCatching {
        val d = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(stamp)!!
        SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(d)
    }.getOrDefault(f.name)
}
