package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.PhoneCheck

/**
 * A short list of what the phone needs, ticked or crossed, with a button to fix each thing. It
 * checks again once a second, so it updates when you come back from Android's settings.
 */
@Composable
fun PhoneCheckCard(tick: Long) {
    val ctx = LocalContext.current
    val items = remember(tick / 10) { PhoneCheck.items(ctx) }
    val tip = remember { PhoneCheck.brandTip() }
    val allGood = items.all { it.ok }
    InfoCard {
        Text(
            if (allGood) "Everything the app needs is on." else "A few things need fixing so the app keeps working while you drive.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (allGood) Ui.good else Ui.sand,
        )
        for (item in items) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(if (item.ok) "✓" else "✕", color = if (item.ok) Ui.good else Ui.red, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
                    Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
                    val fix = item.fix
                    if (!item.ok && fix != null) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { PhoneCheck.fix(ctx, fix) }) { Text(item.fixLabel) }
                    }
                }
            }
        }
        if (tip != null) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("On this phone", style = MaterialTheme.typography.labelLarge, color = Ui.dim)
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = Ui.fg)
            }
        }
    }
}
