package io.github.byjoelsamuel.drivesanity.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.byjoelsamuel.drivesanity.BuildConfig
import io.github.byjoelsamuel.drivesanity.update.Updater
import io.github.byjoelsamuel.drivesanity.update.Updater.Status

/**
 * The "a new version is out" box. It offers the update (Update, Not now, Skip this version), shows the
 * download as it happens, and when it's done offers to install it. Shown over whatever screen the app is on.
 */
@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val status = Updater.status
    if (!Updater.shouldPrompt(ctx)) return
    val later = { Updater.dismissed = true }

    fun openPage(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    when (status) {
        is Status.Available -> UpdateBox(
            title = "Update available",
            onDismiss = later,
            confirm = "Update" to { Updater.download(ctx) },
            dismiss = "Not now" to later,
            extra = "Skip this version" to { Updater.skip(ctx, status.release) },
        ) {
            Text(
                "DriveSanity ${status.release.version} is out. You have ${BuildConfig.VERSION_NAME}.",
                style = MaterialTheme.typography.bodyLarge, color = Ui.fg,
            )
            if (status.release.notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("What's new", style = MaterialTheme.typography.labelMedium, color = Ui.dim)
                Column(Modifier.heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                    Text(notesText(status.release.notes), style = MaterialTheme.typography.bodySmall, color = Ui.dim)
                }
            }
            if (status.release.sizeBytes > 0) {
                Spacer(Modifier.height(8.dp))
                Text("About %.0f MB to download.".format(status.release.sizeBytes / 1048576.0), style = MaterialTheme.typography.bodySmall, color = Ui.dim)
            }
        }

        is Status.Downloading -> UpdateBox(
            title = "Downloading ${status.release.version}",
            onDismiss = later,
            confirm = null,
            dismiss = "Hide" to later,
            extra = "Cancel download" to { Updater.cancelDownload() },
        ) {
            val f = if (status.total > 0) (status.done.toFloat() / status.total).coerceIn(0f, 1f) else 0f
            Bar(f, Ui.accent)
            Spacer(Modifier.height(8.dp))
            Text(
                if (status.total > 0) "%d%% of %.1f MB".format((f * 100).toInt(), status.total / 1048576.0) else "Starting…",
                style = MaterialTheme.typography.bodySmall, color = Ui.dim,
            )
        }

        is Status.Ready -> UpdateBox(
            title = "Ready to install",
            onDismiss = later,
            confirm = "Install" to { Updater.install(ctx, status.file) },
            dismiss = "Later" to later,
            extra = "Send to another app…" to { Updater.share(ctx, status.file) },
        ) {
            Text("DriveSanity ${status.release.version} is downloaded.", style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
            Spacer(Modifier.height(10.dp))
            Text(
                "Using Android Auto? Install it with UnknownAuto, the way you did before, so Android Auto keeps listing the app: pick UnknownAuto when Android asks, or use \"Send to another app\". A copy is also in your Downloads folder.",
                style = MaterialTheme.typography.bodySmall, color = Ui.dim,
            )
        }

        is Status.Failed -> UpdateBox(
            title = "Couldn't update",
            onDismiss = later,
            confirm = if (status.release != null) "Try again" to { Updater.download(ctx) } else null,
            dismiss = "Close" to later,
            extra = "Open the download page" to { openPage(status.release?.pageUrl ?: Updater.PAGE) },
        ) {
            Text(status.message, style = MaterialTheme.typography.bodyMedium, color = Ui.fg)
        }

        else -> {}
    }
}

@Composable
private fun UpdateBox(
    title: String,
    onDismiss: () -> Unit,
    confirm: Pair<String, () -> Unit>?,
    dismiss: Pair<String, () -> Unit>,
    extra: Pair<String, () -> Unit>?,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.card,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Ui.fg) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                content()
                if (extra != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = extra.second, modifier = Modifier.padding(0.dp)) { Text(extra.first, color = Ui.dim) }
                }
            }
        },
        confirmButton = {
            if (confirm != null) TextButton(onClick = confirm.second) { Text(confirm.first, color = Ui.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = dismiss.second) { Text(dismiss.first, color = Ui.dim) } },
    )
}

/** GitHub's release notes are Markdown; this trims the symbols so they read as plain lines. */
private fun notesText(md: String): String = md.lines()
    .map { it.trim().trimStart('#', '*', '-', ' ').replace("**", "").replace("`", "") }
    .filter { it.isNotEmpty() }
    .joinToString("\n")
    .take(900)
