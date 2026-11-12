package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.DashView
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.Snapshot
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.obd.Obd

@Composable
fun SetupTab(
    s: Snapshot,
    units: Units,
    carLayout: Prefs.Layout,
    obdName: String?,
    @Suppress("UNUSED_PARAMETER") tick: Long, // the OBD2 status line is live
    actions: PhoneActions,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Setup", style = MaterialTheme.typography.titleLarge, color = Ui.fg)

        SectionTitle("Calibration")
        InfoCard {
            Text(
                when {
                    s.calibrating -> "Hold still…"
                    s.calibrated -> "Calibrated. Do it again if you move the phone mount."
                    else -> "Not calibrated yet."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Ui.fg,
            )
            Spacer(Modifier.height(8.dp))
            Steps(
                "Mount the phone firmly: facing you, or flat with its top pointing forward.",
                "Stop the car, ideally on level ground.",
                "Tap Calibrate and keep still for a second.",
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = actions::calibrate, enabled = !s.calibrating) { Text("Calibrate") }
        }

        SectionTitle("OBD2 adapter")
        InfoCard {
            Text(obdSentence(), style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
            Spacer(Modifier.height(8.dp))
            Steps(
                "Plug the adapter into the OBD2 port under the dashboard and turn the car on.",
                "Classic Bluetooth adapters: pair it in your phone's Bluetooth settings first (PIN 1234 or 0000). Bluetooth LE ones don't need this.",
                "Choose it below. After that it connects by itself.",
            )
            Spacer(Modifier.height(12.dp))
            Text("Diagnostics", style = MaterialTheme.typography.labelLarge, color = Ui.dim)
            Text(Obd.report(), style = MaterialTheme.typography.bodySmall, color = Ui.dim)
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = actions::pickObd) { Text(if (obdName == null) "Choose adapter" else "Change adapter") }
                if (obdName != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = actions::forgetObd) { Text("Forget $obdName", color = Ui.clay) }
                }
            }
        }

        SectionTitle("Units")
        InfoCard {
            Segments(listOf("MPH · °F · mi", "km/h · °C · km"), if (units == Units.MPH) 0 else 1) { i ->
                if ((i == 0) != (units == Units.MPH)) actions.swapUnits()
            }
        }

        SectionTitle("Startup animation")
        InfoCard {
            val ctx = LocalContext.current
            var on by remember { mutableStateOf(Prefs.startupAnim(ctx)) }
            Text("A short old-TV power-on when the app opens and when Android Auto connects.", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
            Spacer(Modifier.height(10.dp))
            Segments(listOf("On", "Off"), if (on) 0 else 1) { i ->
                on = i == 0
                actions.setStartupAnim(on)
            }
        }

        SectionTitle("Car screen")
        InfoCard {
            Text("What Android Auto shows. The phone always uses this app view.", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
            Spacer(Modifier.height(10.dp))
            val layouts = Prefs.Layout.entries
            Segments(layouts.map { it.title }, layouts.indexOf(carLayout)) { actions.setCarLayout(layouts[it]) }
            Spacer(Modifier.height(8.dp))
            Text(
                when (carLayout) {
                    Prefs.Layout.GAUGES -> "Speed, braking, RPM, the four boxes, a g-meter and fuel."
                    Prefs.Layout.MAP -> "A live map with directions, plus gear, speed, RPM and fuel."
                    Prefs.Layout.RETRO -> "Everything in Saab green, with an early-2000s digital dash."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
            Spacer(Modifier.height(10.dp))
            AndroidView(
                factory = { DashView(it) },
                modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(12.dp)),
            )
        }

        SectionTitle("Android Auto")
        InfoCard {
            Text(
                "Android Auto only shows this app if it was installed through UnknownAuto, so install every update that way too. The bar on the left with the clock belongs to Android Auto and can't be hidden.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.dim,
            )
        }

        SectionTitle("About")
        InfoCard {
            Text("DriveSanity ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
            Text(
                "Map data © OpenStreetMap contributors. Directions by OSRM (FOSSGIS), search by Nominatim, speed limits via Overpass. Font: Poppins (SIL Open Font License).",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Steps(vararg steps: String) {
    steps.forEachIndexed { i, step ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = Ui.accent, modifier = Modifier.width(22.dp))
            Text(step, style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
        }
    }
}

private fun obdSentence(): String {
    val st = Obd.status
    return when (st.link) {
        Obd.Link.OFF -> "Not running."
        Obd.Link.NOT_SET -> "No adapter chosen yet."
        Obd.Link.NO_PERMISSION -> "Needs the Nearby devices permission. Tap Choose adapter."
        Obd.Link.BLUETOOTH_OFF -> "Bluetooth is off."
        Obd.Link.CONNECTING -> "Connecting to ${st.detail}…"
        Obd.Link.LIVE -> "Connected: ${st.detail}"
        Obd.Link.RETRYING -> "Not connected: ${st.detail}. Retrying every few seconds."
    }
}
