package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A rounded card with an optional small title. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = Ui.card,
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Ui.dim)
                Spacer(Modifier.height(6.dp))
            }
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Ui.fg,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

/** A big number with a small unit after it. */
@Composable
fun BigValue(value: String, unit: String, color: Color = Ui.fg, size: Int = 30) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = color, lineHeight = size.sp)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.bodyMedium, color = Ui.dim, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** A plain rounded bar, filled to [fraction]. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Ui.line, cornerRadius = r)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) drawRoundRect(color, size = Size(size.width * f, size.height), cornerRadius = r)
    }
}

/** A small status pill: a coloured dot and a word or two. */
@Composable
fun StatusPill(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Ui.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Ui.fg)
    }
}

/** A choice of a few options, one selected, as a row of rounded segments. */
@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ui.bg).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Ui.raised else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Ui.fg else Ui.dim)
            }
        }
    }
}

enum class TabIcon { DRIVE, TRACK, TRIPS, SETUP }

/** Simple line icons for the bottom bar, drawn so there's no icon library to pull in. */
@Composable
fun TabGlyph(icon: TabIcon, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        when (icon) {
            TabIcon.DRIVE -> {
                drawArc(color, 150f, 240f, false, topLeft = Offset(w * 0.1f, w * 0.12f), size = Size(w * 0.8f, w * 0.8f), style = stroke)
                drawLine(color, Offset(w * 0.5f, w * 0.52f), Offset(w * 0.72f, w * 0.32f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            TabIcon.TRACK -> {
                // A chequered flag on a pole.
                drawLine(color, Offset(w * 0.2f, w * 0.12f), Offset(w * 0.2f, w * 0.9f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                val cell = w * 0.18f
                for (r in 0..2) for (c in 0..3) {
                    if ((r + c) % 2 == 0) drawRect(color, Offset(w * 0.3f + c * cell, w * 0.16f + r * cell), Size(cell, cell))
                }
                drawRect(color, Offset(w * 0.3f, w * 0.16f), Size(cell * 4, cell * 3), style = Stroke(w * 0.05f))
            }
            TabIcon.TRIPS -> {
                for (k in 0..2) {
                    val y = w * (0.25f + k * 0.25f)
                    drawCircle(color, w * 0.06f, Offset(w * 0.15f, y))
                    drawLine(color, Offset(w * 0.32f, y), Offset(w * 0.88f, y), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                }
            }
            TabIcon.SETUP -> {
                val knobs = listOf(0.65f, 0.3f, 0.55f)
                for (k in 0..2) {
                    val y = w * (0.25f + k * 0.25f)
                    drawLine(color, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                    drawCircle(Ui.bg, w * 0.11f, Offset(w * knobs[k], y))
                    drawCircle(color, w * 0.11f, Offset(w * knobs[k], y), style = Stroke(w * 0.08f))
                }
            }
        }
    }
}
