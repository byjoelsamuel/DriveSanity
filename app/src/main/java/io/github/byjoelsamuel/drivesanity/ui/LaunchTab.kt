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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.Snapshot
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.track.Launch
import io.github.byjoelsamuel.drivesanity.track.Phase

/** The 0–60 timer: its state, an optional 3, 2, 1, and the last run. */
@Composable
fun LaunchTab(s: Snapshot, units: Units) {
    val ctx = LocalContext.current
    val l = s.launch
    var countdown by remember { mutableStateOf(Prefs.launchCountdown(ctx)) }
    fun sec(ms: Long?) = if (ms == null) "–" else "%.2f s".format(java.util.Locale.US, ms / 1000.0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("0–60", style = MaterialTheme.typography.titleLarge, color = Ui.fg)

        SectionTitle("Timer")
        InfoCard {
            Text(
                when {
                    l == null || s.speedMps == null -> "Waiting for the car's speed."
                    else -> when (l.phase) {
                        Phase.READY -> if (countdown) "Ready. Press Start 3-2-1, then go." else "Ready. Floor it when you like."
                        Phase.COUNTDOWN -> "Get ready…"
                        Phase.GO -> "GO"
                        Phase.RUNNING -> "Timing…"
                        Phase.DONE -> "Done."
                        Phase.MOVING -> "Come to a stop to arm it."
                        Phase.TOO_EARLY -> "Too early. Wait for the GO."
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Ui.fg,
            )
            when (l?.phase) {
                Phase.COUNTDOWN -> BigValue((l.count ?: 3).toString(), "", size = 56)
                Phase.RUNNING -> BigValue(sec(l.liveMs), "", size = 44)
                else -> {}
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (countdown) Button(onClick = { Launch.startCountdown() }) { Text("Start 3-2-1") }
                OutlinedButton(onClick = { Launch.reset() }) { Text("Clear run") }
            }
            Spacer(Modifier.height(14.dp))
            Text("Countdown before a run", style = MaterialTheme.typography.labelLarge, color = Ui.dim)
            Spacer(Modifier.height(6.dp))
            Segments(listOf("Off", "On"), if (countdown) 1 else 0) {
                countdown = it == 1
                Prefs.setLaunchCountdown(ctx, countdown)
            }
            Text(
                "With it on, \"Start 3-2-1\" (here, or the first button on the car's 0–60 screen) counts 3, 2, 1, GO. Moving off before the GO is called too early. With it off, just launch: the clock starts when the car moves.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionTitle("Last run")
        InfoCard {
            if (l?.hasResult != true) {
                Text(
                    "Stop the car, then launch. 0–60 mph and 0–100 km/h are timed on their own from the moment the car moves off. It uses the car's own speed when the OBD2 adapter is connected, and GPS, which is slower to react, when it isn't.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ui.dim,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column {
                        Text("0–60 mph", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                        BigValue(sec(l.to60Ms), "", color = if (l.to60Ms == null) Ui.dim else Ui.good, size = 34)
                    }
                    Column {
                        Text("0–100 km/h", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                        BigValue(sec(l.to100Ms), "", color = if (l.to100Ms == null) Ui.dim else Ui.good, size = 34)
                    }
                }
                RunShare(l.to60Ms, l.to100Ms, units)
            }
            Text(
                "Do it somewhere safe and legal.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
