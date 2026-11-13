package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The building blocks of the Settings tab: grouped lists of rows, each with a small tinted icon,
 * a title, a line of live state and a chevron, the way iOS and Instagram lay out their settings.
 */

/** The little pictures on the left of each settings row. Drawn here so no icon library is needed. */
enum class SettingsIcon { CAR, CALIBRATE, UNITS, PLUG, CODES, SHIELD, PHONE, SCREEN, MAP, BELL, SOUND, MUSIC, FLASK, WHEEL, INFO, RESET, STORAGE, LINK }

/** A group of rows on one rounded card, with an optional small heading above it. */
@Composable
fun SettingsGroup(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Ui.dim,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
        )
    } else {
        Spacer(Modifier.height(16.dp))
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Ui.card) {
        Column(content = content)
    }
    if (footer != null) {
        Text(
            footer,
            style = MaterialTheme.typography.bodySmall,
            color = Ui.dim,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
        )
    }
}

/** A thin line between two rows, starting under the text rather than under the icon. */
@Composable
fun RowDivider() {
    Box(Modifier.padding(start = 62.dp).fillMaxWidth().height(1.dp).background(Ui.line))
}

@Composable
private fun IconTile(icon: SettingsIcon, tint: Color) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) { drawSettingsIcon(icon, tint) }
    }
}

/** A tappable row that opens a page. [value] is the live state, shown under the title. */
@Composable
fun SettingsRow(
    icon: SettingsIcon,
    title: String,
    value: String? = null,
    tint: Color = Ui.accent,
    valueColor: Color = Ui.dim,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ui.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!value.isNullOrEmpty()) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Chevron(Ui.dim, left = false)
    }
}

/** A row with an on/off switch, for settings that are only ever one or the other. */
@Composable
fun SwitchRow(title: String, detail: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ui.fg)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Ui.dim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ui.bg,
                checkedTrackColor = Ui.accent,
                checkedBorderColor = Ui.accent,
                uncheckedThumbColor = Ui.dim,
                uncheckedTrackColor = Ui.raised,
                uncheckedBorderColor = Ui.line,
            ),
        )
    }
}

/** A row where one of several is picked: a filled dot beside the chosen one. */
@Composable
fun ChoiceRow(title: String, detail: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (selected) Ui.fg else Ui.dim)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Ui.dim)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (selected) Ui.accent else Ui.line),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(Ui.bg))
        }
    }
}

/** Free text inside a group, padded to line up with the rows. */
@Composable
fun GroupBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
}

/** A small tinted state chip, for the summary at the top of Settings. */
@Composable
fun StateChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Ui.fg)
    }
}

/** The frame of a settings page: a back arrow, the page's name, then the scrolling content. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack).semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { Chevron(Ui.fg, left = true) }
        Spacer(Modifier.width(4.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ui.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Chevron(color: Color, left: Boolean) {
    Canvas(Modifier.size(14.dp, 20.dp)) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (left) {
                moveTo(w * 0.8f, h * 0.18f); lineTo(w * 0.2f, h * 0.5f); lineTo(w * 0.8f, h * 0.82f)
            } else {
                moveTo(w * 0.2f, h * 0.18f); lineTo(w * 0.8f, h * 0.5f); lineTo(w * 0.2f, h * 0.82f)
            }
        }
        drawPath(p, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawSettingsIcon(icon: SettingsIcon, c: Color) {
    val w = size.width
    val sw = w * 0.1f
    val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(w * x, w * y)
    when (icon) {
        SettingsIcon.CAR -> {
            val body = Path().apply {
                moveTo(w * 0.08f, w * 0.64f); lineTo(w * 0.14f, w * 0.44f); lineTo(w * 0.3f, w * 0.3f)
                lineTo(w * 0.7f, w * 0.3f); lineTo(w * 0.86f, w * 0.44f); lineTo(w * 0.92f, w * 0.64f)
                lineTo(w * 0.92f, w * 0.7f); lineTo(w * 0.08f, w * 0.7f); close()
            }
            drawPath(body, c, style = line)
            drawCircle(c, w * 0.09f, p(0.28f, 0.74f)); drawCircle(c, w * 0.09f, p(0.72f, 0.74f))
        }
        SettingsIcon.CALIBRATE -> {
            drawCircle(c, w * 0.3f, p(0.5f, 0.5f), style = line)
            drawCircle(c, w * 0.07f, p(0.5f, 0.5f))
            for ((a, b) in listOf((0.5f to 0.04f) to (0.5f to 0.16f), (0.5f to 0.84f) to (0.5f to 0.96f), (0.04f to 0.5f) to (0.16f to 0.5f), (0.84f to 0.5f) to (0.96f to 0.5f))) {
                drawLine(c, p(a.first, a.second), p(b.first, b.second), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
        SettingsIcon.UNITS -> {
            drawRoundRect(c, p(0.06f, 0.34f), Size(w * 0.88f, w * 0.32f), CornerRadius(w * 0.08f), style = line)
            for (i in 0..3) {
                val x = 0.22f + i * 0.19f
                drawLine(c, p(x, 0.34f), p(x, if (i % 2 == 0) 0.52f else 0.46f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            }
        }
        SettingsIcon.PLUG -> {
            drawLine(c, p(0.36f, 0.08f), p(0.36f, 0.3f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.64f, 0.08f), p(0.64f, 0.3f), strokeWidth = sw, cap = StrokeCap.Round)
            drawRoundRect(c, p(0.22f, 0.3f), Size(w * 0.56f, w * 0.28f), CornerRadius(w * 0.08f), style = line)
            drawLine(c, p(0.5f, 0.58f), p(0.5f, 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.CODES -> {
            val tri = Path().apply { moveTo(w * 0.5f, w * 0.1f); lineTo(w * 0.93f, w * 0.86f); lineTo(w * 0.07f, w * 0.86f); close() }
            drawPath(tri, c, style = line)
            drawLine(c, p(0.5f, 0.38f), p(0.5f, 0.6f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(c, w * 0.05f, p(0.5f, 0.74f))
        }
        SettingsIcon.SHIELD -> {
            val s = Path().apply {
                moveTo(w * 0.5f, w * 0.06f); lineTo(w * 0.86f, w * 0.2f); lineTo(w * 0.86f, w * 0.5f)
                cubicTo(w * 0.86f, w * 0.74f, w * 0.68f, w * 0.88f, w * 0.5f, w * 0.95f)
                cubicTo(w * 0.32f, w * 0.88f, w * 0.14f, w * 0.74f, w * 0.14f, w * 0.5f)
                lineTo(w * 0.14f, w * 0.2f); close()
            }
            drawPath(s, c, style = line)
            drawLine(c, p(0.36f, 0.5f), p(0.47f, 0.62f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.47f, 0.62f), p(0.66f, 0.38f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.PHONE -> {
            drawRoundRect(c, p(0.27f, 0.06f), Size(w * 0.46f, w * 0.88f), CornerRadius(w * 0.1f), style = line)
            drawLine(c, p(0.43f, 0.82f), p(0.57f, 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.SCREEN -> {
            drawRoundRect(c, p(0.06f, 0.16f), Size(w * 0.88f, w * 0.54f), CornerRadius(w * 0.1f), style = line)
            drawLine(c, p(0.5f, 0.7f), p(0.5f, 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.3f, 0.86f), p(0.7f, 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.MAP -> {
            val pin = Path().apply {
                moveTo(w * 0.5f, w * 0.94f)
                cubicTo(w * 0.2f, w * 0.6f, w * 0.18f, w * 0.46f, w * 0.18f, w * 0.38f)
                cubicTo(w * 0.18f, w * 0.18f, w * 0.34f, w * 0.06f, w * 0.5f, w * 0.06f)
                cubicTo(w * 0.66f, w * 0.06f, w * 0.82f, w * 0.18f, w * 0.82f, w * 0.38f)
                cubicTo(w * 0.82f, w * 0.46f, w * 0.8f, w * 0.6f, w * 0.5f, w * 0.94f)
                close()
            }
            drawPath(pin, c, style = line)
            drawCircle(c, w * 0.1f, p(0.5f, 0.38f))
        }
        SettingsIcon.BELL -> {
            val bell = Path().apply {
                moveTo(w * 0.2f, w * 0.72f); cubicTo(w * 0.28f, w * 0.64f, w * 0.28f, w * 0.5f, w * 0.28f, w * 0.42f)
                cubicTo(w * 0.28f, w * 0.24f, w * 0.38f, w * 0.14f, w * 0.5f, w * 0.14f)
                cubicTo(w * 0.62f, w * 0.14f, w * 0.72f, w * 0.24f, w * 0.72f, w * 0.42f)
                cubicTo(w * 0.72f, w * 0.5f, w * 0.72f, w * 0.64f, w * 0.8f, w * 0.72f); close()
            }
            drawPath(bell, c, style = line)
            drawLine(c, p(0.42f, 0.86f), p(0.58f, 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.SOUND -> {
            val sp = Path().apply {
                moveTo(w * 0.1f, w * 0.4f); lineTo(w * 0.26f, w * 0.4f); lineTo(w * 0.44f, w * 0.24f)
                lineTo(w * 0.44f, w * 0.76f); lineTo(w * 0.26f, w * 0.6f); lineTo(w * 0.1f, w * 0.6f); close()
            }
            drawPath(sp, c, style = line)
            drawArc(c, -40f, 80f, false, topLeft = p(0.34f, 0.3f), size = Size(w * 0.4f, w * 0.4f), style = line)
            drawArc(c, -45f, 90f, false, topLeft = p(0.34f, 0.12f), size = Size(w * 0.56f, w * 0.76f), style = line)
        }
        SettingsIcon.MUSIC -> {
            drawLine(c, p(0.62f, 0.14f), p(0.62f, 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.62f, 0.14f), p(0.84f, 0.24f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(c, w * 0.15f, p(0.46f, 0.74f))
        }
        SettingsIcon.FLASK -> {
            val f = Path().apply {
                moveTo(w * 0.4f, w * 0.08f); lineTo(w * 0.4f, w * 0.38f); lineTo(w * 0.12f, w * 0.84f)
                lineTo(w * 0.88f, w * 0.84f); lineTo(w * 0.6f, w * 0.38f); lineTo(w * 0.6f, w * 0.08f)
            }
            drawPath(f, c, style = line)
            drawLine(c, p(0.32f, 0.08f), p(0.68f, 0.08f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.WHEEL -> {
            drawCircle(c, w * 0.42f, p(0.5f, 0.5f), style = line)
            drawCircle(c, w * 0.1f, p(0.5f, 0.5f))
            drawLine(c, p(0.1f, 0.5f), p(0.4f, 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.6f, 0.5f), p(0.9f, 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.5f, 0.6f), p(0.5f, 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.RESET -> {
            drawArc(c, -50f, 290f, false, topLeft = p(0.14f, 0.14f), size = Size(w * 0.72f, w * 0.72f), style = line)
            drawLine(c, p(0.377f, 0.162f), p(0.261f, 0.311f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(c, p(0.377f, 0.162f), p(0.55f, 0.1f), strokeWidth = sw, cap = StrokeCap.Round)
        }
        SettingsIcon.STORAGE -> {
            for (k in 0..2) {
                val y = 0.1f + k * 0.28f
                drawRoundRect(c, p(0.12f, y), Size(w * 0.76f, w * 0.22f), CornerRadius(w * 0.07f), style = line)
                drawCircle(c, w * 0.04f, p(0.72f, y + 0.11f))
            }
        }
        SettingsIcon.LINK -> {
            val box = Path().apply { moveTo(w * 0.72f, w * 0.6f); lineTo(w * 0.72f, w * 0.84f); lineTo(w * 0.14f, w * 0.84f); lineTo(w * 0.14f, w * 0.28f); lineTo(w * 0.4f, w * 0.28f) }
            drawPath(box, c, style = line)
            drawLine(c, p(0.44f, 0.56f), p(0.86f, 0.14f), strokeWidth = sw, cap = StrokeCap.Round)
            val head = Path().apply { moveTo(w * 0.58f, w * 0.14f); lineTo(w * 0.86f, w * 0.14f); lineTo(w * 0.86f, w * 0.42f) }
            drawPath(head, c, style = line)
        }
        SettingsIcon.INFO -> {
            drawCircle(c, w * 0.42f, p(0.5f, 0.5f), style = line)
            drawLine(c, p(0.5f, 0.46f), p(0.5f, 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(c, w * 0.055f, p(0.5f, 0.3f))
        }
    }
}

/** A "Are you sure?" box in the app's own colours. [destructive] paints the confirm button red. */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ui.card,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Ui.fg) },
        text = content,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) Ui.red else Ui.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = Ui.dim) }
        },
    )
}

/** The bar that slides up over Settings while there are changes waiting: how many, Discard, and Apply. */
@Composable
fun ApplyBar(count: Int, onDiscard: () -> Unit, onApply: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Ui.raised, shadowElevation = 8.dp) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ui.sand))
            Spacer(Modifier.width(10.dp))
            Text(
                if (count == 1) "1 unsaved change" else "$count unsaved changes",
                style = MaterialTheme.typography.bodyMedium,
                color = Ui.fg,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onDiscard) { Text("Discard", color = Ui.dim) }
            androidx.compose.material3.Button(onClick = onApply) { Text("Apply") }
        }
    }
}

/** A small pill that confirms something happened ("3 changes applied"). */
@Composable
fun NoticePill(text: String) {
    Surface(shape = CircleShape, color = Ui.raised, shadowElevation = 6.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ui.good))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Ui.fg)
        }
    }
}

/** "Reset to default" at the foot of a page. Greyed out when the page is already on its defaults. */
@Composable
fun ResetRow(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Reset to default", style = MaterialTheme.typography.bodyLarge, color = if (enabled) Ui.clay else Ui.dim.copy(alpha = 0.5f))
    }
}
