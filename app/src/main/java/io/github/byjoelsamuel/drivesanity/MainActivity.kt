package io.github.byjoelsamuel.drivesanity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.byjoelsamuel.drivesanity.obd.Obd
import io.github.byjoelsamuel.drivesanity.obd.showObdPicker

class MainActivity : Activity() {
    private lateinit var statsText: TextView
    private lateinit var recButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Colors.BG)
        }
        val dash = DashView(this).apply {
            setOnClickListener { Prefs.toggleUnits(context) }
        }
        root.addView(dash, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        statsText = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(Colors.FG)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(statsText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            textSize = 12f
            minWidth = 0
            setPadding(dp(2), paddingTop, dp(2), paddingBottom)
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        button("Calibrate") { DriveEngine.calibrate() }
        recButton = button("Rec") { DriveEngine.toggleRecording(this); refresh() }
        button("Share") { shareLog() }
        button("OBD2") { pickObd() }
        button("Reset") { toast("Long-press to reset the trip") }.setOnLongClickListener {
            DriveEngine.stats.reset()
            toast("Trip reset")
            true
        }
        root.addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        // Android 15 draws apps under the status and navigation bars; keep clear of them.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        askForPermissions()
    }

    override fun onStart() {
        super.onStart()
        DriveEngine.hold(this, DriveEngine.PHONE)
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        DriveEngine.release(DriveEngine.PHONE)
        super.onStop()
    }

    private fun askForPermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_REQUEST) {
            if (Obd.hasBluetoothPermission(this)) showObdPicker(this) else toast("OBD2 needs the Nearby devices permission")
            return
        }
        DriveEngine.onPermissionsChanged()
        if (!hasLocationPermission(this)) toast("Without location there's no speed, only g-force")
    }

    /** Asks for Bluetooth (Android 12+ calls it "Nearby devices") if needed, then lists adapters. */
    private fun pickObd() {
        if (Build.VERSION.SDK_INT >= 31 && !Obd.hasBluetoothPermission(this)) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                BLUETOOTH_REQUEST,
            )
            return
        }
        showObdPicker(this)
    }

    private fun refresh() {
        val u = Prefs.units(this)
        val st = DriveEngine.stats
        statsText.text = buildString {
            appendLine("TRIP   ${oneDecimal(u.distance(st.distanceM))} ${u.distanceLabel}   moving ${hms(st.movingSec)}")
            appendLine("SPEED  avg ${u.speed(st.avgMovingSpeedMps).toInt()}   top ${u.speed(st.topSpeedMps).toInt()} ${u.label}")
            appendLine("PEAK   brake %.2f  accel %.2f  turn %.2f g".format(st.peakBrakeG, st.peakAccelG, st.peakTurnG))
            appendLine("HARD   brakes ${st.hardBrakes.count}  launches ${st.hardLaunches.count}  turns ${st.hardTurns.count}")
            appendLine(Obd.describe())
            append(DriveEngine.recordingFile?.let { "● REC  ${it.name}" } ?: DriveEngine.status)
        }
        recButton.text = if (DriveEngine.recording) "Stop" else "Rec"
    }

    private fun shareLog() {
        if (DriveEngine.recording) return toast("Stop recording first")
        val file = TripLog.newest(filesDir) ?: return toast("Nothing recorded yet. Tap Rec first.")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, file.name))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        const val BLUETOOTH_REQUEST = 2
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

/** The phone's dashboard: redraws itself about 30 times a second. Tap it to switch km/h and mph. */
class DashView(context: Context) : View(context) {
    private val painter = DashPainter()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        painter.draw(canvas, rect, DriveEngine.snapshot(), Prefs.units(context))
        postInvalidateDelayed(33)
    }
}
