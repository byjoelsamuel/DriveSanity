package io.github.byjoelsamuel.drivesanity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.content.FileProvider
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.showNavigate
import io.github.byjoelsamuel.drivesanity.obd.Obd
import io.github.byjoelsamuel.drivesanity.obd.showObdPicker
import io.github.byjoelsamuel.drivesanity.ui.DriveTheme
import io.github.byjoelsamuel.drivesanity.ui.PhoneActions
import io.github.byjoelsamuel.drivesanity.ui.PhoneApp
import java.io.File

/** The phone app. Its screens are in ui/; the car's are in car/. */
class MainActivity : ComponentActivity(), PhoneActions {

    private val askLocationPermission = registerForActivityResult(RequestMultiplePermissions()) {
        DriveEngine.onPermissionsChanged()
        if (!hasLocationPermission(this)) toast("Without location there's no speed, only g-force")
    }

    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            Prefs.setCustomSound(this, uri.toString())
            Prefs.setStartupSound(this, StartupSoundMode.CUSTOM)
            toast("Sound chosen")
        }
    }

    private val askBluetoothPermission = registerForActivityResult(RequestMultiplePermissions()) {
        if (Obd.hasBluetoothPermission(this)) showObdPicker(this) else toast("OBD2 needs the Nearby devices permission")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // "tab": open on Drive (0), 0–60 (1), Trips (2) or Settings (3).
        setContent { DriveTheme { PhoneApp(this, intent.getIntExtra("tab", 0)) } }
        if (savedInstanceState == null) StartupSound.play(this)
        askLocation()
    }

    override fun onStart() {
        super.onStart()
        DriveEngine.hold(this, DriveEngine.PHONE)
    }

    override fun onStop() {
        DriveEngine.release(DriveEngine.PHONE)
        super.onStop()
    }

    // --- PhoneActions -----------------------------------------------------------------------

    override fun calibrate() = DriveEngine.calibrate()

    override fun toggleRecording() {
        val wasRecording = DriveEngine.recording
        DriveEngine.toggleRecording(this)
        if (wasRecording) toast("Saved. It's in the Trips tab.")
    }

    override fun swapUnits() = Prefs.toggleUnits(this)

    override fun setStartupAnim(on: Boolean) = Prefs.setStartupAnim(this, on)

    override fun setCar(id: String) = DriveEngine.setCar(this, id)

    override fun setAlerts(on: Boolean) = Prefs.setAlerts(this, on)

    override fun setStartupSound(mode: StartupSoundMode) = Prefs.setStartupSound(this, mode)

    override fun chooseSoundFile() = pickSound.launch(arrayOf("audio/*"))

    override fun playStartupSound() = StartupSound.play(this)

    override fun setRedline(rpm: Float) = DriveEngine.setRedline(this, rpm)

    override fun setCarLayout(layout: Prefs.Layout) {
        while (Prefs.layout(this) != layout) Prefs.toggleLayout(this)
    }

    /** Asks for Bluetooth (Android 12+ calls it "Nearby devices") if needed, then lists adapters. */
    override fun pickObd() {
        if (Build.VERSION.SDK_INT >= 31 && !Obd.hasBluetoothPermission(this)) {
            askBluetoothPermission.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
            return
        }
        showObdPicker(this)
    }

    override fun readCodes() {
        Obd.requestReadCodes()
        toast("Reading fault codes…")
    }

    override fun setDemo(on: Boolean) {
        if (on) {
            if (!io.github.byjoelsamuel.drivesanity.demo.Demo.start(this)) toast("Couldn't start the demo")
        } else {
            io.github.byjoelsamuel.drivesanity.demo.Demo.stop()
        }
    }

    override fun setRawRecording(on: Boolean) {
        DriveEngine.setRawRecording(this, on)
        toast(if (on) "Recording a raw session" else "Saved")
    }

    override fun playRaw(file: File) {
        if (!io.github.byjoelsamuel.drivesanity.demo.Demo.start(this, file)) toast("Couldn't play that file")
    }

    override fun probeCar() {
        Obd.requestProbe()
        toast("Asking the car. The answer shows in Setup in a few seconds.")
    }

    override fun forgetObd() {
        Prefs.saveObdDevice(this, null)
        Obd.restart()
    }

    override fun navigate() = showNavigate(this)

    override fun endRoute() = Nav.stop()

    override fun askLocation() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) askLocationPermission.launch(wanted.toTypedArray())
    }

    override fun share(file: File) {
        if (DriveEngine.recordingFile == file) return toast("Stop recording first")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (file.name.endsWith(".csv")) "text/csv" else "text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, file.name))
    }

    override fun delete(file: File) {
        if (DriveEngine.recordingFile == file) return toast("Stop recording first")
        file.delete()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
