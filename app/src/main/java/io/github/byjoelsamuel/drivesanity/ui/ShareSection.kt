package io.github.byjoelsamuel.drivesanity.ui

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.Session
import io.github.byjoelsamuel.drivesanity.ShareCard
import io.github.byjoelsamuel.drivesanity.ShareImage
import io.github.byjoelsamuel.drivesanity.Units
import io.github.byjoelsamuel.drivesanity.track.RunView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Makes a picture of a drive, to post. It shows what will be posted, and where it goes. */
@Composable
fun SessionShare(file: File, session: Session, units: Units) {
    val ctx = LocalContext.current
    var format by remember { mutableStateOf(ShareCard.Format.STORY) }
    var card by remember { mutableIntStateOf(0) } // 0 the drive, 1 the laps
    var hideEnds by remember { mutableStateOf(true) }
    val hasLaps = session.laps.isNotEmpty()
    val kind = if (card == 1 && hasLaps) 1 else 0
    val bmp by produceState<Bitmap?>(null, file, format, kind, hideEnds, units) {
        value = null
        value = withContext(Dispatchers.Default) {
            if (kind == 1) ShareCard.laps(ctx, session, shortDate(file), format, hideEnds)
            else ShareCard.drive(ctx, session, driveTitle(file), shortDate(file), units, format, hideEnds)
        }
    }

    InfoCard {
        Text("A picture of the drive for Instagram or anywhere else. It's made on your phone: nothing is uploaded.", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
        Spacer(Modifier.height(12.dp))
        Segments(ShareCard.Format.entries.map { "${it.label} ${if (it == ShareCard.Format.STORY) "9:16" else "4:5"}" }, format.ordinal) { format = ShareCard.Format.entries[it] }
        if (hasLaps) {
            Spacer(Modifier.height(8.dp))
            Segments(listOf("The drive", "The laps"), kind) { card = it }
        }
        Spacer(Modifier.height(8.dp))
        Segments(listOf("Hide where it starts and ends", "Show the whole path"), if (hideEnds) 0 else 1) { hideEnds = it == 0 }
        Text(
            if (hideEnds) "The first and last 250 m of the path are left out, so the picture doesn't show where you live. There are no map tiles, street names or coordinates on it either."
            else "The whole path is drawn, start and end included. Think before posting it, since drives usually start at home.",
            style = MaterialTheme.typography.bodySmall,
            color = if (hideEnds) Ui.dim else Ui.sand,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(12.dp))
        val b = bmp
        if (b == null) {
            Text("Making the picture…", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
        } else {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "The picture that will be shared",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.62f).aspectRatio(b.width.toFloat() / b.height).clip(RoundedCornerShape(14.dp)).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            ShareButtons(b, "drivesanity-${file.nameWithoutExtension}-${format.name.lowercase()}-$kind")
        }
    }
}

/** Instagram, any app, and the photo library. */
@Composable
fun ShareButtons(bmp: Bitmap, name: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val f = ShareImage.write(ctx, bmp, name)
            if (!ShareImage.toInstagram(ctx, f)) {
                Toast.makeText(ctx, "Instagram isn't installed, so here's the share sheet", Toast.LENGTH_SHORT).show()
                ShareImage.share(ctx, f)
            }
        }) { Text("Instagram") }
        OutlinedButton(onClick = { ShareImage.share(ctx, ShareImage.write(ctx, bmp, name)) }) { Text("Share…") }
        if (Build.VERSION.SDK_INT >= 29) {
            OutlinedButton(onClick = {
                val ok = ShareImage.saveToPhotos(ctx, bmp, name)
                Toast.makeText(ctx, if (ok) "Saved to Pictures/DriveSanity" else "Couldn't save it", Toast.LENGTH_SHORT).show()
            }) { Text("Save") }
        }
    }
}

/** A picture of a standing-start result. */
@Composable
fun RunShare(run: RunView, units: Units) {
    val ctx = LocalContext.current
    var bmp by remember(run) { mutableStateOf<Bitmap?>(null) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (bmp == null) {
            OutlinedButton(onClick = {
                bmp = ShareCard.run(ctx, run, SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()).uppercase(), units, ShareCard.Format.STORY)
            }) { Text("Make a picture") }
        }
    }
    bmp?.let { b ->
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "The picture that will be shared",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(b.width.toFloat() / b.height).clip(RoundedCornerShape(14.dp)).padding(top = 8.dp),
        )
        Spacer(Modifier.width(0.dp))
        Spacer(Modifier.height(8.dp))
        ShareButtons(b, "drivesanity-run-${System.currentTimeMillis() / 1000}")
    }
}

private fun parsed(f: File): Date? = runCatching {
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(f.name.removePrefix("drive-").removeSuffix(".csv"))
}.getOrNull()

/** "11 NOV 2026". */
private fun shortDate(f: File): String =
    parsed(f)?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(it).uppercase() } ?: ""

/** "Evening drive", from when it started. */
private fun driveTitle(f: File): String {
    val d = parsed(f) ?: return "Drive"
    val hour = java.util.Calendar.getInstance().apply { time = d }.get(java.util.Calendar.HOUR_OF_DAY)
    val part = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    return "$part drive"
}
