package io.github.byjoelsamuel.drivesanity.obd

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Toast
import io.github.byjoelsamuel.drivesanity.Prefs

/**
 * The one-time "which one is your OBD2 adapter?" list: paired devices, plus Bluetooth LE
 * adapters found nearby during a 10-second scan (those don't need pairing). The choice is
 * saved, and from then on [Obd] connects to it by itself.
 */
@SuppressLint("MissingPermission")
fun showObdPicker(activity: Activity) {
    val adapter = activity.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter == null) {
        Toast.makeText(activity, "This phone has no Bluetooth", Toast.LENGTH_SHORT).show()
        return
    }
    if (!adapter.isEnabled) {
        Toast.makeText(activity, "Turn Bluetooth on first", Toast.LENGTH_SHORT).show()
        return
    }

    data class Found(val device: BluetoothDevice, val name: String, val ble: Boolean, val paired: Boolean)

    val found = mutableListOf<Found>()
    val labels = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1)
    val likely = Regex("OBD|ELM|LINK|VGATE|ICAR|VEEPEAK|KONNWEI|CARISTA|BAFX|SCAN", RegexOption.IGNORE_CASE)

    fun refresh() {
        found.sortWith(compareBy<Found>({ !likely.containsMatchIn(it.name) }, { !it.paired }, { it.name }))
        labels.clear()
        labels.addAll(found.map { f ->
            val kind = if (f.ble) "Bluetooth LE" else "Bluetooth"
            "${f.name}\n${f.device.address} · $kind${if (f.paired) " · paired" else ""}"
        })
    }

    for (d in adapter.bondedDevices.orEmpty()) {
        found += Found(d, d.name ?: d.address, d.type == BluetoothDevice.DEVICE_TYPE_LE, paired = true)
    }
    refresh()

    val handler = Handler(Looper.getMainLooper())
    val scanner = adapter.bluetoothLeScanner
    val scan = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = result.scanRecord?.deviceName ?: d.name ?: return // unnamed devices aren't adapters worth listing
            if (found.any { it.device.address == d.address }) return
            found += Found(d, name, ble = true, paired = false)
            handler.post { refresh() }
        }
    }
    fun stopScan() = runCatching { scanner?.stopScan(scan) }

    val current = Prefs.obdDevice(activity)
    val dialog = AlertDialog.Builder(activity)
        .setTitle("Pick your OBD2 adapter")
        .setAdapter(labels) { _, which ->
            stopScan()
            val f = found[which]
            Prefs.saveObdDevice(activity, Prefs.ObdDevice(f.device.address, f.name, f.ble))
            Obd.restart()
            Toast.makeText(activity, "Connecting to ${f.name}…", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Cancel", null)
        .apply {
            if (current != null) {
                setNeutralButton("Forget ${current.name}") { _, _ ->
                    Prefs.saveObdDevice(activity, null)
                    Obd.restart()
                }
            }
        }
        .create()
    dialog.setOnDismissListener { stopScan() }
    dialog.show()

    runCatching { scanner?.startScan(scan) }
    handler.postDelayed({ stopScan() }, 10_000)
}
