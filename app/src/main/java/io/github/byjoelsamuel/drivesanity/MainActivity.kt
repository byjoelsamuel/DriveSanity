package io.github.byjoelsamuel.drivesanity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TabStopSpan
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
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.byjoelsamuel.drivesanity.nav.showNavigate
import io.github.byjoelsamuel.drivesanity.obd.Obd
import io.github.byjoelsamuel.drivesanity.obd.showObdPicker

class MainActivity : Activity() {
    private lateinit var statsText: TextView
    private lateinit var recButton: Button
    private lateinit var layoutButton: Button
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
            typeface = Fonts.medium(context)
            textSize = 13f
            setLineSpacing(0f, 1.15f)
            setTextColor(Colors.FG)
            setPadding(dp(20), dp(8), dp(20), dp(10))
        }
        root.addView(statsText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(12))
        }
        // Flat, rounded buttons in the dashboard's colours.
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            typeface = Fonts.medium(context)
            textSize = 13f
            setTextColor(Colors.FG)
            background = GradientDrawable().apply {
                setColor(Colors.BOX)
                cornerRadius = dp(10).toFloat()
            }
            stateListAnimator = null
            minWidth = 0
            minHeight = dp(44)
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { onClick() }
            buttons.addView(this, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        button("Calibrate") { DriveEngine.calibrate() }
        recButton = button("Rec") { DriveEngine.toggleRecording(this); refresh() }
        layoutButton = button("Map") { Prefs.toggleLayout(this); refresh() }
        button("Navigate") { showNavigate(this) }
        lateinit var more: Button
        more = button("⋯") { showMore(more) }
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
        val out = SpannableStringBuilder()
        fun row(label: String, value: String) {
            if (out.isNotEmpty()) out.append("\n")
            val start = out.length
            out.append(label)
            out.setSpan(ForegroundColorSpan(Colors.DIM), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.append("\t").append(value)
        }
        row("Trip", "${oneDecimal(u.distance(st.distanceM))} ${u.distanceLabel}  ·  ${hms(st.movingSec)} moving")
        row("Speed", "avg ${u.speed(st.avgMovingSpeedMps).toInt()}  ·  top ${u.speed(st.topSpeedMps).toInt()} ${u.label}")
        row("Peak g", "brake %.2f  ·  accel %.2f  ·  turn %.2f".format(st.peakBrakeG, st.peakAccelG, st.peakTurnG))
        row("Hard", "${st.hardBrakes.count} brakes  ·  ${st.hardLaunches.count} launches  ·  ${st.hardTurns.count} turns")
        row("OBD2", Obd.describe().removePrefix("OBD2: "))
        row("Status", DriveEngine.recordingFile?.let { "Recording ${it.name}" } ?: DriveEngine.status)
        // Line the values up after the labels.
        out.setSpan(TabStopSpan.Standard(dp(64)), 0, out.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        statsText.text = out

        recButton.text = if (DriveEngine.recording) "Stop" else "Rec"
        layoutButton.text = if (Prefs.layout(this) == Prefs.Layout.MAP) "Gauges" else "Map"
        recButton.setTextColor(if (DriveEngine.recording) Colors.RED else Colors.FG)
    }

    /** The less-used actions. */
    private fun showMore(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Share last recording").setOnMenuItemClickListener { shareLog(); true }
            menu.add("OBD2 adapter").setOnMenuItemClickListener { pickObd(); true }
            menu.add("Reset trip").setOnMenuItemClickListener {
                DriveEngine.stats.reset()
                toast("Trip reset")
                true
            }
            show()
        }
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
    private val painter = DashPainter(context)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        painter.draw(canvas, rect, DriveEngine.snapshot(), Prefs.units(context), Prefs.layout(context))
        postInvalidateDelayed(33)
    }
}
