package io.github.byjoelsamuel.drivesanity.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.CarProfiles
import io.github.byjoelsamuel.drivesanity.DashView
import io.github.byjoelsamuel.drivesanity.PhoneCheck
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.Revs
import io.github.byjoelsamuel.drivesanity.Snapshot
import io.github.byjoelsamuel.drivesanity.StartupSoundMode
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.demo.RawRecorder
import io.github.byjoelsamuel.drivesanity.map.Tiles
import io.github.byjoelsamuel.drivesanity.media.MediaWatcher
import io.github.byjoelsamuel.drivesanity.obd.Dtc
import io.github.byjoelsamuel.drivesanity.obd.Obd
import kotlin.math.roundToInt

/** The pages of Settings. HOME is the list; every other one opens from a row on it. */
private enum class Page(val title: String) {
    HOME("Settings"),
    CAR("My car"),
    CALIBRATION("Calibration"),
    UNITS("Units"),
    ADAPTER("OBD2 adapter"),
    FAULTS("Fault codes"),
    BATTERY("Battery and read-only"),
    PHONE_CHECK("Phone check"),
    SCREENS("Car screens"),
    MAP("Map look"),
    ALERTS("Alerts and shift light"),
    STARTUP("Startup"),
    MUSIC("Music"),
    DEMO("Demo and recordings"),
    AUTO("Android Auto"),
    ABOUT("About"),
}

/**
 * Settings, laid out like a phone's own settings app: a short list of grouped rows, each showing
 * its current state, and each opening its own page. Back (the arrow or the system gesture) returns
 * to the list.
 */
@Composable
fun SettingsTab(
    s: Snapshot,
    units: Units,
    carLayout: Prefs.Layout,
    obdName: String?,
    tick: Long, // the OBD2 status line and the phone check are live
    actions: PhoneActions,
) {
    // The fault codes and extra readings are only asked for while this screen is open.
    DisposableEffect(Unit) {
        Obd.wantDiagnostics("phone", true)
        onDispose { Obd.wantDiagnostics("phone", false) }
    }
    var saved by rememberSaveable { mutableStateOf(Page.HOME.name) }
    val page = Page.entries.firstOrNull { it.name == saved } ?: Page.HOME
    BackHandler(enabled = page != Page.HOME) { saved = Page.HOME.name }

    AnimatedContent(
        targetState = page,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val forward = targetState != Page.HOME
            if (forward) {
                (slideInHorizontally(tween()) { it / 5 } + fadeIn(tween())) togetherWith (slideOutHorizontally(tween()) { -it / 8 } + fadeOut(tween())) using SizeTransform(clip = false)
            } else {
                (slideInHorizontally(tween()) { -it / 8 } + fadeIn(tween())) togetherWith (slideOutHorizontally(tween()) { it / 5 } + fadeOut(tween())) using SizeTransform(clip = false)
            }
        },
        label = "settings",
    ) { p ->
        if (p == Page.HOME) {
            Home(s, units, carLayout, obdName, tick) { saved = it.name }
        } else {
            PageFrame(p.title, onBack = { saved = Page.HOME.name }) {
                when (p) {
                    Page.CAR -> CarPage(tick, actions)
                    Page.CALIBRATION -> CalibrationPage(s, actions)
                    Page.UNITS -> UnitsPage(units, actions)
                    Page.ADAPTER -> AdapterPage(obdName, tick, actions)
                    Page.FAULTS -> FaultsPage(tick, actions)
                    Page.BATTERY -> BatteryPage(tick)
                    Page.PHONE_CHECK -> PhoneCheckCard(tick)
                    Page.SCREENS -> ScreensPage(carLayout, actions)
                    Page.MAP -> MapPage(tick)
                    Page.ALERTS -> AlertsPage(tick, actions)
                    Page.STARTUP -> StartupPage(tick, actions)
                    Page.MUSIC -> MusicPage(tick)
                    Page.DEMO -> DemoPage(s, tick, actions)
                    Page.AUTO -> AutoPage()
                    Page.ABOUT -> AboutPage()
                    Page.HOME -> {}
                }
            }
        }
    }
}

private fun <T> tween() = androidx.compose.animation.core.tween<T>(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing)

@Composable
private fun PageFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        PageHeader(title, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            Spacer(Modifier.height(4.dp))
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

// --- The list ---------------------------------------------------------------------------------

@Composable
private fun Home(
    s: Snapshot,
    units: Units,
    carLayout: Prefs.Layout,
    obdName: String?,
    tick: Long,
    open: (Page) -> Unit,
) {
    val ctx = LocalContext.current
    val car = CarProfiles.byId(Prefs.carId(ctx))
    val link = Obd.status.link
    val phoneItems = remember(tick / 10) { PhoneCheck.items(ctx) }
    val toFix = phoneItems.count { !it.ok }
    val report = Obd.dtcReport
    val access = MediaWatcher.hasAccess(ctx)
    val soundMode = Prefs.startupSound(ctx)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Ui.fg, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(14.dp))

        // A summary of the car and whether everything is ready.
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Ui.card) {
            Column(Modifier.padding(18.dp)) {
                Text(car.name, style = MaterialTheme.typography.titleLarge, color = Ui.fg)
                Text(car.subtitle, style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (link) {
                        Obd.Link.LIVE -> StateChip("Adapter live", Ui.good) { open(Page.ADAPTER) }
                        Obd.Link.SLEEPING -> StateChip("Car asleep", Ui.accent) { open(Page.ADAPTER) }
                        Obd.Link.CONNECTING -> StateChip("Connecting", Ui.sand) { open(Page.ADAPTER) }
                        Obd.Link.NOT_SET -> StateChip("No adapter", Ui.sand) { open(Page.ADAPTER) }
                        else -> StateChip("Adapter offline", Ui.sand) { open(Page.ADAPTER) }
                    }
                    if (toFix == 0) StateChip("Phone ready", Ui.good) { open(Page.PHONE_CHECK) }
                    else StateChip("$toFix to fix", Ui.sand) { open(Page.PHONE_CHECK) }
                }
            }
        }

        SettingsGroup("Car and sensors") {
            SettingsRow(SettingsIcon.CAR, "My car", "Gear logic and redline", Ui.accent) { open(Page.CAR) }
            RowDivider()
            SettingsRow(
                SettingsIcon.CALIBRATE, "Calibration",
                when { s.calibrating -> "Hold still…"; s.calibrated -> "Calibrated"; else -> "Not calibrated yet" },
                Ui.good, if (s.calibrated || s.calibrating) Ui.dim else Ui.sand,
            ) { open(Page.CALIBRATION) }
            RowDivider()
            SettingsRow(SettingsIcon.UNITS, "Units", if (units == Units.MPH) "MPH · °F · mi" else "km/h · °C · km", Ui.sand) { open(Page.UNITS) }
        }

        SettingsGroup("Connection") {
            SettingsRow(SettingsIcon.PLUG, "OBD2 adapter", obdShort(obdName), Ui.accent, if (link == Obd.Link.LIVE) Ui.good else Ui.dim) { open(Page.ADAPTER) }
            RowDivider()
            SettingsRow(
                SettingsIcon.CODES, "Fault codes",
                when {
                    link != Obd.Link.LIVE -> "Connect the adapter to read"
                    report == null -> "Reading…"
                    report.all.isEmpty() -> "None"
                    else -> "${report.all.size} found"
                },
                Ui.clay,
            ) { open(Page.FAULTS) }
            RowDivider()
            SettingsRow(
                SettingsIcon.SHIELD, "Battery and read-only",
                if (Prefs.letCarSleep(ctx)) "Car sleeps when off" else "Car stays awake",
                Ui.good,
            ) { open(Page.BATTERY) }
            RowDivider()
            SettingsRow(
                SettingsIcon.PHONE, "Phone check",
                if (toFix == 0) "All good" else "$toFix to fix",
                Ui.accent, if (toFix == 0) Ui.good else Ui.sand,
            ) { open(Page.PHONE_CHECK) }
        }

        SettingsGroup("On the car screen") {
            SettingsRow(SettingsIcon.SCREEN, "Car screens", carLayout.title, Ui.accent) { open(Page.SCREENS) }
            RowDivider()
            SettingsRow(SettingsIcon.MAP, "Map look", if (Prefs.modernMap(ctx)) "Modern" else "Classic", Ui.good) { open(Page.MAP) }
            RowDivider()
            SettingsRow(SettingsIcon.BELL, "Alerts and shift light", if (Prefs.alerts(ctx)) "On" else "Off", Ui.clay) { open(Page.ALERTS) }
            RowDivider()
            SettingsRow(
                SettingsIcon.SOUND, "Startup",
                (if (Prefs.startupAnim(ctx)) "Animation on" else "Animation off") + " · " + soundMode.title,
                Ui.sand,
            ) { open(Page.STARTUP) }
            RowDivider()
            SettingsRow(
                SettingsIcon.MUSIC, "Music",
                if (access) "Notification access on" else "Needs notification access",
                Ui.accent, if (access) Ui.good else Ui.sand,
            ) { open(Page.MUSIC) }
        }

        SettingsGroup("More") {
            SettingsRow(SettingsIcon.FLASK, "Demo and recordings", if (s.demo) "Demo is playing" else "Try the app without a car", Ui.sand, if (s.demo) Ui.good else Ui.dim) { open(Page.DEMO) }
            RowDivider()
            SettingsRow(SettingsIcon.WHEEL, "Android Auto", "Installing for the car", Ui.accent) { open(Page.AUTO) }
            RowDivider()
            SettingsRow(SettingsIcon.INFO, "About", "DriveSanity ${BuildConfig.VERSION_NAME}", Ui.dim) { open(Page.ABOUT) }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun obdShort(name: String?): String = when (Obd.status.link) {
    Obd.Link.LIVE -> "Connected" + (name?.let { " · $it" } ?: "")
    Obd.Link.CONNECTING -> "Connecting…"
    Obd.Link.RETRYING -> "Not connected, retrying"
    Obd.Link.SLEEPING -> "Car is off, so it's asleep"
    Obd.Link.NOT_SET -> "None chosen yet"
    Obd.Link.NO_PERMISSION -> "Needs the Nearby devices permission"
    Obd.Link.BLUETOOTH_OFF -> "Bluetooth is off"
    Obd.Link.OFF -> "Not running"
}

// --- Car and sensors --------------------------------------------------------------------------

@Composable
private fun CarPage(tick: Long, actions: PhoneActions) {
    val ctx = LocalContext.current
    val car = CarProfiles.byId(Prefs.carId(ctx))
    SettingsGroup(footer = car.note) {
        CarProfiles.all.forEachIndexed { i, p ->
            if (i > 0) RowDivider()
            ChoiceRow(p.name, p.subtitle, p.id == car.id) { actions.setCar(p.id) }
        }
    }
    SettingsGroup("Redline", footer = "Sets where the red zone starts on the rev bar.") {
        GroupBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("%,d rpm".format(Revs.REDLINE.roundToInt()), style = MaterialTheme.typography.titleLarge, color = Ui.fg)
                }
                OutlinedButton(onClick = { actions.setRedline(Revs.REDLINE - 100f) }) { Text("−") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { actions.setRedline(Revs.REDLINE + 100f) }) { Text("+") }
            }
            if (!car.redlineChecked && Prefs.redline(ctx, car.id) == null) {
                Text(
                    "This is a starting guess. Use − and + to match the red zone on your tachometer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ui.sand,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CalibrationPage(s: Snapshot, actions: PhoneActions) {
    SettingsGroup {
        GroupBody {
            Text(
                when {
                    s.calibrating -> "Hold still…"
                    s.calibrated -> "Calibrated. Do it again if you move the phone mount."
                    else -> "Not calibrated yet."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (s.calibrated) Ui.good else Ui.fg,
            )
            Spacer(Modifier.height(10.dp))
            Steps(
                "Mount the phone firmly: facing you, or flat with its top pointing forward.",
                "Stop the car, ideally on level ground.",
                "Tap Calibrate and keep still for a second.",
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = actions::calibrate, enabled = !s.calibrating) { Text("Calibrate") }
        }
    }
}

@Composable
private fun UnitsPage(units: Units, actions: PhoneActions) {
    SettingsGroup(footer = "Applies to the phone, the car screen, trips and the pictures you share.") {
        ChoiceRow("MPH · °F · mi", "Miles per hour, Fahrenheit, miles", units == Units.MPH) { if (units != Units.MPH) actions.swapUnits() }
        RowDivider()
        ChoiceRow("km/h · °C · km", "Kilometres per hour, Celsius, kilometres", units != Units.MPH) { if (units == Units.MPH) actions.swapUnits() }
    }
}

// --- Connection -------------------------------------------------------------------------------

@Composable
private fun AdapterPage(obdName: String?, tick: Long, actions: PhoneActions) {
    val ctx = LocalContext.current
    SettingsGroup {
        GroupBody {
            Text(obdSentence(), style = MaterialTheme.typography.bodyLarge, color = if (Obd.status.link == Obd.Link.LIVE) Ui.good else Ui.fg)
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = actions::pickObd) { Text(if (obdName == null) "Choose adapter" else "Change adapter") }
                if (obdName != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = actions::forgetObd) { Text("Forget $obdName", color = Ui.clay) }
                }
            }
        }
    }
    SettingsGroup("Setting up") {
        GroupBody {
            Steps(
                "Plug the adapter into the OBD2 port under the dashboard and turn the car on.",
                "Classic Bluetooth adapters: pair it in your phone's Bluetooth settings first (PIN 1234 or 0000). Bluetooth LE ones don't need this.",
                "Choose it above. After that it connects by itself.",
            )
        }
    }
    SettingsGroup("Diagnostics") {
        GroupBody {
            Text(Obd.report(), style = MaterialTheme.typography.bodySmall, color = Ui.dim)
        }
    }
    SettingsGroup(
        "Fuel and tires",
        footer = if (!Prefs.advancedReads(ctx)) "Turn on \"Manufacturer-specific reads\" under Battery and read-only first." else null,
    ) {
        GroupBody {
            Text(
                "Fuel level and tire pressure aren't in the standard data your Mazda shares. This asks the instrument cluster for the few readings other Mazdas have answered with. It only reads, and changes nothing. Do it parked with the engine running; the replies appear in Diagnostics above.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = actions::probeCar, enabled = Obd.status.link == Obd.Link.LIVE && Prefs.advancedReads(ctx)) { Text("Ask the car for fuel and tires") }
        }
    }
}

@Composable
private fun FaultsPage(tick: Long, actions: PhoneActions) {
    val report = Obd.dtcReport
    val live = Obd.status.link == Obd.Link.LIVE
    SettingsGroup {
        GroupBody {
            when {
                !live -> Text("Connect the adapter to read the car's fault codes.", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
                report == null -> Text("Reading…", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
                else -> {
                    val lamp = when (report.milOn) { true -> "on"; false -> "off"; null -> "unknown" }
                    Text(
                        if (report.all.isEmpty()) "No fault codes. Check-engine lamp $lamp." else "${report.all.size} fault code(s). Check-engine lamp $lamp.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (report.all.isEmpty()) Ui.good else Ui.fg,
                    )
                    for (code in report.all) {
                        Row(Modifier.padding(top = 8.dp)) {
                            Text(code, style = MaterialTheme.typography.titleSmall, color = Ui.sand, modifier = Modifier.width(72.dp))
                            Text(Dtc.describe(code), style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = actions::readCodes, enabled = live) { Text("Read codes again") }
        }
    }
}

@Composable
private fun BatteryPage(tick: Long) {
    val ctx = LocalContext.current
    SettingsGroup {
        GroupBody {
            Text(
                "The app only ever asks the car questions. It never clears fault codes, changes a setting or starts a test; anything like that is blocked inside the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.fg,
            )
        }
    }
    var sleep by remember { mutableStateOf(Prefs.letCarSleep(ctx)) }
    SettingsGroup(
        "The car's battery",
        footer = "Asking a car questions keeps its computers awake, which drains the battery and can stop a dashcam from going into parking mode. With this on, the app stops asking once the engine has been off for a little while and only reads the adapter's own voltage, then wakes when you open the app, drive off, or the voltage shows the engine running. Leave it on.",
    ) {
        SwitchRow("Let the car sleep", "Stop asking once the engine is off", sleep) { sleep = it; Prefs.setLetCarSleep(ctx, it) }
    }
    var adv by remember { mutableStateOf(Prefs.advancedReads(ctx)) }
    SettingsGroup(
        "Extra readings",
        footer = "Fuel level and tire pressure aren't in the standard data. This lets the app send the few extra read requests other Mazdas have answered. They are still only reads, and they are off until you turn them on.",
    ) {
        SwitchRow("Manufacturer-specific reads", "Off by default", adv) { adv = it; Prefs.setAdvancedReads(ctx, it) }
    }
    SettingsGroup {
        GroupBody {
            Text(
                "Adapter: ${Prefs.obdDevice(ctx)?.name ?: "none chosen"}. Unplug it if the car sits for days, since cheap adapters keep drawing a little current.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
        }
    }
}

// --- On the car screen ------------------------------------------------------------------------

@Composable
private fun ScreensPage(carLayout: Prefs.Layout, actions: PhoneActions) {
    SettingsGroup(footer = "What Android Auto shows. The phone always uses this app view. In the car, the \"Show\" button on the top row steps through them.") {
        Prefs.Layout.entries.forEachIndexed { i, l ->
            if (i > 0) RowDivider()
            ChoiceRow(l.title, layoutNote(l), l == carLayout) { actions.setCarLayout(l) }
        }
    }
    SettingsGroup("Preview") {
        AndroidView(
            factory = { DashView(it) },
            modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(20.dp)),
        )
    }
}

private fun layoutNote(l: Prefs.Layout) = when (l) {
    Prefs.Layout.GAUGES -> "Bar gauges for RPM, throttle, coolant and fuel, a huge gear number, speed, g-force and tire pressures."
    Prefs.Layout.MAP -> "Directions and a live map on the left half, speed, gear, RPM and the rest on the right."
    Prefs.Layout.RETRO -> "Everything in Saab green, with an early-2000s digital dash."
    Prefs.Layout.DIAG -> "The check-engine lamp, fault codes, live engine readings and car info. For parked moments."
    Prefs.Layout.LAUNCH -> "READY, an optional 3-2-1, then your 0–60 mph and 0–100 km/h time. The countdown is on the 0–60 tab."
    Prefs.Layout.MUSIC -> "The cover, song, artist and progress. Previous, play or pause and next are the car's own buttons."
}

@Composable
private fun MapPage(tick: Long) {
    val ctx = LocalContext.current
    var modern by remember { mutableStateOf(Prefs.modernMap(ctx)) }
    SettingsGroup(footer = "Both need mobile data. Modern is by CARTO, from OpenStreetMap data, fetched sharp for the car screen. Classic is the standard OpenStreetMap map turned dark.") {
        ChoiceRow("Modern", "A dark map styled for dashboards", modern) { modern = true; Prefs.setModernMap(ctx, true); Tiles.setModern(true) }
        RowDivider()
        ChoiceRow("Classic", "OpenStreetMap, turned dark", !modern) { modern = false; Prefs.setModernMap(ctx, false); Tiles.setModern(false) }
    }
}

@Composable
private fun AlertsPage(tick: Long, actions: PhoneActions) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Prefs.alerts(ctx)) }
    SettingsGroup(footer = "A flashing banner for a hot engine, low battery, low tire, low fuel or speeding, and a shift light along the top of the car screen as the revs near the redline.") {
        SwitchRow("Alerts and shift light", null, on) { on = it; actions.setAlerts(it) }
    }
}

@Composable
private fun StartupPage(tick: Long, actions: PhoneActions) {
    val ctx = LocalContext.current
    var anim by remember { mutableStateOf(Prefs.startupAnim(ctx)) }
    SettingsGroup(footer = "A short old-TV power-on when the app opens and when Android Auto connects.") {
        SwitchRow("Startup animation", null, anim) { anim = it; actions.setStartupAnim(it) }
    }
    val mode = Prefs.startupSound(ctx)
    SettingsGroup(
        "Sound",
        footer = "The chime is a sound I made, not a recording of a real reader. To use a real one, record it or find one yourself and choose it under My file.",
    ) {
        GroupBody {
            Text(
                when (mode) {
                    StartupSoundMode.OFF -> "No sound."
                    StartupSoundMode.CHIME -> "A short rising chime, in the spirit of a Japanese ETC card reader. In the car it plays through the speakers."
                    StartupSoundMode.VOICE -> "The chime, then \"ETC card inserted\" in Japanese, spoken by your phone's Japanese voice if it has one. Without one you just get the chime."
                    StartupSoundMode.CUSTOM -> if (Prefs.customSound(ctx) == null) "Choose a sound file below." else "Plays the sound file you chose."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.dim,
            )
            Spacer(Modifier.height(12.dp))
            Segments(StartupSoundMode.entries.map { it.title }, mode.ordinal) { actions.setStartupSound(StartupSoundMode.entries[it]) }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = actions::playStartupSound) { Text("Play it") }
                if (mode == StartupSoundMode.CUSTOM) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = actions::chooseSoundFile) { Text("Choose file") }
                }
            }
        }
    }
}

@Composable
private fun MusicPage(tick: Long) {
    val ctx = LocalContext.current
    MediaWatcher.poll(ctx)
    val granted = MediaWatcher.hasAccess(ctx)
    val np = MediaWatcher.now
    SettingsGroup {
        GroupBody {
            Text(
                when {
                    !granted -> "Notification access is off."
                    np == null -> "Notification access is on. Nothing is playing."
                    else -> "Now playing: ${np.title}${if (np.artist.isNotEmpty()) " · ${np.artist}" else ""} (${np.appName.ifEmpty { np.pkg }})"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (granted) Ui.good else Ui.sand,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text(if (granted) "Notification access settings" else "Allow notification access") }
        }
    }
    SettingsGroup("How it works") {
        GroupBody {
            Text(
                "The Music screen shows what's playing in YouTube Music or any other music app, with the cover and the app's icon. To read it, Android needs you to allow DriveSanity under notification access. The app only looks at what's playing: it doesn't read, keep or send your notifications.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.dim,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "On the car, previous, play or pause and next are the car's own buttons down the right edge of the Music screen, so the knob and the screen can press them.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
        }
    }
}

// --- More -------------------------------------------------------------------------------------

@Composable
private fun DemoPage(s: Snapshot, tick: Long, actions: PhoneActions) {
    SettingsGroup {
        GroupBody {
            Text(
                "Demo mode plays a made-up drive through the whole app in place of the car, the GPS and the motion sensor: a standing start, then a loop. Every screen, the 0–60 timer and the car screen run off it, so you can try the app or show it to someone with no car.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.dim,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.demo) {
                    Button(onClick = { actions.setDemo(false) }) { Text("Stop demo") }
                    Spacer(Modifier.width(10.dp))
                    Text("Playing", style = MaterialTheme.typography.bodyMedium, color = Ui.good)
                } else {
                    Button(onClick = { actions.setDemo(true) }) { Text("Start demo") }
                }
            }
        }
    }
    val rawOn = RawRecorder.on
    SettingsGroup("Raw recording") {
        GroupBody {
            Text(
                "Writes everything the GPS, the motion sensor and the adapter report into a small file you can send me for a bug report, or play back as a demo. It's off until you start it.",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { actions.setRawRecording(!rawOn) }) { Text(if (rawOn) "Stop recording" else "Start recording") }
                val last = RawRecorder.file
                if (!rawOn && last != null && last.exists()) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { actions.share(last) }) { Text("Share last") }
                    TextButton(onClick = { actions.playRaw(last) }) { Text("Play it") }
                }
            }
        }
    }
}

@Composable
private fun AutoPage() {
    SettingsGroup {
        GroupBody {
            Text(
                "Android Auto only shows this app if it was installed through UnknownAuto, so install every update that way too. The bar on the left with the clock belongs to Android Auto and can't be hidden.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.dim,
            )
        }
    }
}

@Composable
private fun AboutPage() {
    SettingsGroup {
        GroupBody {
            Text("DriveSanity ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
            Spacer(Modifier.height(6.dp))
            Text(
                "Map data © OpenStreetMap contributors. Directions by OSRM (FOSSGIS), search by Nominatim, speed limits via Overpass. Font: Poppins (SIL Open Font License).",
                style = MaterialTheme.typography.bodySmall,
                color = Ui.dim,
            )
        }
    }
}

// --- Shared bits ------------------------------------------------------------------------------

@Composable
private fun Steps(vararg steps: String) {
    steps.forEachIndexed { i, step ->
        Row(Modifier.padding(vertical = 3.dp)) {
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
        Obd.Link.SLEEPING -> "The car is off, so the app isn't asking it anything and the car can sleep. It wakes when you open the app or drive off."
    }
}
