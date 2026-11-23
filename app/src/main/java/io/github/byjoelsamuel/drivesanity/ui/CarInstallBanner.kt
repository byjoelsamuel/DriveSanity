package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown on Home when this copy of the app was installed without the Google Play mark. The phone app
 * works fine, but Android Auto quietly refuses to list it, which looks like "the app doesn't work in the car".
 * Nothing is shown when the mark is there, or when it can't be told.
 */
@Composable
fun CarInstallBanner(playMark: Boolean?, unknownAutoInstalled: Boolean, onOpenUnknownAuto: () -> Unit, onSteps: () -> Unit) {
    if (playMark != false) return
    InfoCard(title = "Android Auto can't see DriveSanity") {
        Text(
            "This copy was installed without the Google Play mark, so the car won't list it. The phone app works as normal. " +
                "Install the latest DriveSanity.apk with UnknownAuto, with Shizuku running, to fix it.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ui.fg,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unknownAutoInstalled) {
                Button(onClick = onOpenUnknownAuto) { Text("Open UnknownAuto") }
                TextButton(onClick = onSteps) { Text("Show the steps") }
            } else {
                Button(onClick = onSteps) { Text("Show the steps") }
            }
        }
    }
}
