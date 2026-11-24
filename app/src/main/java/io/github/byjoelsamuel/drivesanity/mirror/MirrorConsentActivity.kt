package io.github.byjoelsamuel.drivesanity.mirror

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * A see-through screen that shows Android's own "start sharing" question, then starts the capture and opens
 * Google Maps. Android asks every time; the app can't skip or remember it.
 */
class MirrorConsentActivity : ComponentActivity() {
    private val ask = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            MirrorService.start(this, result.resultCode, data)
            openMaps()
        } else {
            MapMirror.state = MapMirror.State.Off
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // coming back after a rotation: the question is already up
        MapMirror.state = MapMirror.State.Asking
        ask.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    private fun openMaps() {
        val launch = packageManager.getLaunchIntentForPackage(MAPS)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        try {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Sharing is on. Open Google Maps on the phone.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val MAPS = "com.google.android.apps.maps"
    }
}
