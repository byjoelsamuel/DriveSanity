package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.StartupSoundMode
import io.github.byjoelsamuel.drivesanity.TripLog
import kotlinx.coroutines.delay
import java.io.File

/** Everything the phone screens can ask the activity to do. */
interface PhoneActions {
    fun calibrate()
    fun toggleRecording()
    fun swapUnits()
    fun setCarLayout(layout: Prefs.Layout)
    fun setStartupAnim(on: Boolean)
    fun setCar(id: String)
    fun setAlerts(on: Boolean)
    fun setStartupSound(mode: StartupSoundMode)
    fun chooseSoundFile()
    fun playStartupSound()
    fun setRedline(rpm: Float)
    fun pickObd()
    fun readCodes()
    fun probeCar()
    fun forgetObd()
    fun navigate()
    fun endRoute()
    fun askLocation()
    fun share(file: File)
    fun delete(file: File)
}

/**
 * The phone app: three tabs, Drive, Trips and Setup. Separate from the car screen, which keeps
 * its own dashboards. The live numbers refresh ten times a second. The power-on animation plays
 * over it once when the app opens.
 */
@Composable
fun PhoneApp(actions: PhoneActions, startTab: Int = 0) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var introDone by rememberSaveable { mutableStateOf(!Prefs.startupAnim(ctx)) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            tick++
        }
    }
    // A fresh snapshot every tick; screens also get the tick so cards that read live values
    // straight from the engine (route, OBD2, recording time) refresh too.
    val s = remember(tick) { DriveEngine.snapshot() }
    val units = Prefs.units(ctx)
    val recordings = remember(tick / 10) {
        TripLog.dir(ctx.filesDir).listFiles { f -> f.name.endsWith(".csv") }?.sortedByDescending { it.name }.orEmpty() // names are timestamps
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Ui.bg,
            bottomBar = {
                NavigationBar(containerColor = Ui.card) {
                    listOf("Drive" to TabIcon.DRIVE, "Trips" to TabIcon.TRIPS, "Setup" to TabIcon.SETUP).forEachIndexed { i, (label, icon) ->
                        val on = tab == i
                        NavigationBarItem(
                            selected = on,
                            onClick = { tab = i },
                            icon = { TabGlyph(icon, if (on) Ui.fg else Ui.dim) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Ui.raised,
                                selectedTextColor = Ui.fg,
                                unselectedTextColor = Ui.dim,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> DriveTab(s, units, tick, actions, openSetup = { tab = 2 })
                    1 -> TripsTab(units, recordings, tick, actions)
                    else -> SetupTab(s, units, Prefs.layout(ctx), Prefs.obdDevice(ctx)?.name, tick, actions)
                }
            }
        }
        if (!introDone) StartupOverlay { introDone = true }
    }
}
