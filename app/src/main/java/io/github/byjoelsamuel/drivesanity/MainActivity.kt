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

    private val askBluetoothPermission = registerForActivityResult(RequestMultiplePermissions()) {
        if (Obd.hasBluetoothPermission(this)) showObdPicker(this) else toast("OBD2 needs the Nearby devices permission")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // "tab": open on Drive (0), Trips (1) or Setup (2).
        setContent { DriveTheme { PhoneApp(this, intent.getIntExtra("tab", 0)) } }
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
            .setType("text/csv")
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
