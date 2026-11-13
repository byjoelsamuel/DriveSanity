package io.github.byjoelsamuel.drivesanity.ui

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.Block
import io.github.byjoelsamuel.drivesanity.CardData
import io.github.byjoelsamuel.drivesanity.CarProfiles
import io.github.byjoelsamuel.drivesanity.Genre
import io.github.byjoelsamuel.drivesanity.PanelStyle
import io.github.byjoelsamuel.drivesanity.Place
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.Session
import io.github.byjoelsamuel.drivesanity.ShareBackground
import io.github.byjoelsamuel.drivesanity.ShareCard
import io.github.byjoelsamuel.drivesanity.ShareConfig
import io.github.byjoelsamuel.drivesanity.ShareImage
import io.github.byjoelsamuel.drivesanity.TripLog
import io.github.byjoelsamuel.drivesanity.Units
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Makes a picture of a recording, or of all of them together, to post. */
@Composable
fun SessionShare(file: File, session: Session, units: Units) {
    val ctx = LocalContext.current
    val car = remember { CarProfiles.byId(Prefs.carId(ctx)).name }
    var source by remember { mutableIntStateOf(0) } // 0 this drive, 1 all my drives
    var hideEnds by remember { mutableStateOf(true) }
    val all by produceState<List<Session>?>(null, source) {
        value = if (source == 1) withContext(Dispatchers.IO) {
            TripLog.dir(ctx.filesDir).listFiles { f -> f.name.endsWith(".csv") }.orEmpty().map { Session.read(it) }
        } else null
    }
    val data = remember(file, session, source, hideEnds, all, units) {
        if (source == 0) CardData.ofSession(session, driveTitle(file), shortDate(file), car, units, hideEnds)
        else all?.let { CardData.ofProfile(it, car, units, shortDate(file)) }
    }
    InfoCard {
        Text(
            "A picture for Instagram or anywhere else. Pick what goes on it, and put your own photo behind it if you like. It's made on your phone: nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ui.dim,
        )
        Spacer(Modifier.height(12.dp))
        ShareEditor(data, "drivesanity-${file.nameWithoutExtension}") {
            Segments(listOf("This drive", "My profile (all drives)"), source) { source = it }
            if (source == 0) {
                Spacer(Modifier.height(8.dp))
                Segments(listOf("Hide where it starts and ends", "Show the whole path"), if (hideEnds) 0 else 1) { hideEnds = it == 0 }
                Text(
                    if (hideEnds) "The first and last 250 m of the path are left out, so the picture doesn't show where you live. There are no map tiles, street names or coordinates on it either."
                    else "The whole path is drawn, start and end included. Think before posting it, since drives usually start at home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hideEnds) Ui.dim else Ui.sand,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** A picture of a 0–60 result. */
@Composable
fun RunShare(to60Ms: Long?, to100Ms: Long?, units: Units) {
    val ctx = LocalContext.current
    var open by remember(to60Ms, to100Ms) { mutableStateOf(false) }
    val data = remember(to60Ms, to100Ms, units) {
        CardData.ofRun(to60Ms, to100Ms, SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date()).uppercase(), CarProfiles.byId(Prefs.carId(ctx)).name, units)
    }
    if (!open) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Make a picture") }
    } else {
        Spacer(Modifier.height(8.dp))
        ShareEditor(data, "drivesanity-0-60-${System.currentTimeMillis() / 1000}") {}
    }
}

/**
 * The editor: the picture as it will be posted, and everything that changes it. The background is a
 * photo from the gallery (copied into the app); the blocks are grouped by genre, and the blocks of one
 * genre share a panel. What was chosen last time is remembered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareEditor(data: CardData?, name: String, extras: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(ShareConfig.decode(Prefs.shareConfig(ctx))) }
    var format by remember { mutableStateOf(ShareCard.Format.STORY) }
    var photoVersion by remember { mutableIntStateOf(0) }
    fun update(c: ShareConfig) {
        config = c
        Prefs.setShareConfig(ctx, c.encode())
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { ShareBackground.save(ctx, uri) }
            if (!ok) Toast.makeText(ctx, "Couldn't use that picture", Toast.LENGTH_SHORT).show()
            photoVersion++
        }
    }
    val hasPhoto = remember(photoVersion) { ShareBackground.exists(ctx) }

    ColumnScopeBox {
        Segments(ShareCard.Format.entries.map { "${it.label} ${if (it == ShareCard.Format.STORY) "9:16" else "4:5"}" }, format.ordinal) { format = ShareCard.Format.entries[it] }
        Spacer(Modifier.height(8.dp))
        extras()

        Text("Background", style = MaterialTheme.typography.labelLarge, color = Ui.dim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(if (hasPhoto) "Change photo" else "Choose a photo") }
            if (hasPhoto) TextButton(onClick = { ShareBackground.clear(ctx); photoVersion++ }) { Text("Remove", color = Ui.clay) }
        }
        if (hasPhoto) {
            Spacer(Modifier.height(8.dp))
            val levels = listOf(0.25f, 0.5f, 0.75f)
            Segments(listOf("Light", "Medium", "Dark"), levels.indices.minBy { kotlin.math.abs(levels[it] - config.dim) }) { update(config.copy(dim = levels[it])) }
            Text("How dark the photo goes behind the numbers.", style = MaterialTheme.typography.bodySmall, color = Ui.dim, modifier = Modifier.padding(top = 4.dp))
        }

        Text("Panels", style = MaterialTheme.typography.labelLarge, color = Ui.dim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Segments(PanelStyle.entries.map { it.label }, config.panel.ordinal) { update(config.copy(panel = PanelStyle.entries[it])) }
        Spacer(Modifier.height(8.dp))
        Segments(Place.entries.map { it.label }, config.place.ordinal) { update(config.copy(place = Place.entries[it])) }

        Text("What's on it", style = MaterialTheme.typography.labelLarge, color = Ui.dim, modifier = Modifier.padding(top = 14.dp))
        Text("Blocks in the same group share one panel.", style = MaterialTheme.typography.bodySmall, color = Ui.dim, modifier = Modifier.padding(bottom = 4.dp))
        if (data == null) {
            Text("Adding up your drives…", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
        } else {
            for (genre in Genre.entries) {
                val here = Block.of(genre).filter { data.has(it) }
                if (here.isEmpty()) continue
                val allOn = here.all { it in config.blocks }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(genre.label, style = MaterialTheme.typography.titleSmall, color = Ui.fg, modifier = Modifier.weight(1f))
                    TextButton(onClick = { update(config.withGenre(genre, !allOn)) }) { Text(if (allOn) "None" else "All") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in here) FilterChip(selected = b in config.blocks, onClick = { update(config.with(b, b !in config.blocks)) }, label = { Text(b.label) })
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val bmp by produceState<Bitmap?>(null, data, config, format, photoVersion) {
            value = null
            value = data?.let { d -> withContext(Dispatchers.Default) { ShareCard.render(ctx, d, config, format, ShareBackground.load(ctx)) } }
        }
        val b = bmp
        if (b == null) {
            Text("Making the picture…", style = MaterialTheme.typography.bodyMedium, color = Ui.dim)
        } else {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "The picture that will be shared",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.66f).aspectRatio(b.width.toFloat() / b.height).clip(RoundedCornerShape(14.dp)).align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            ShareButtons(b, "$name-${format.name.lowercase()}")
        }
    }
}

/** Lets the editor's pieces use the card's column without repeating the types. */
@Composable
private fun ColumnScopeBox(content: @Composable ColumnScope.() -> Unit) = androidx.compose.foundation.layout.Column(content = content)

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
