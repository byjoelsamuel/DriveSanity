package io.github.byjoelsamuel.drivesanity.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.byjoelsamuel.drivesanity.R

/** The phone app's colours: the same quiet palette as the dashboard. */
object Ui {
    val bg = Color(0xFF121416)
    val card = Color(0xFF1A1D20)
    val raised = Color(0xFF23272B)
    val fg = Color(0xFFE8E6E1)
    val dim = Color(0xFF8A8E94)
    val line = Color(0xFF2B2F33)
    val accent = Color(0xFF8FB3C2)
    val good = Color(0xFF8FAE8B)
    val sand = Color(0xFFD4B577)
    val clay = Color(0xFFD08B63)
    val red = Color(0xFFC9605A)
}

val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_semibold, FontWeight.Bold),
)

private fun TextStyle.poppins() = copy(fontFamily = Poppins)

@Composable
fun DriveTheme(content: @Composable () -> Unit) {
    val t = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ui.accent,
            onPrimary = Ui.bg,
            secondary = Ui.good,
            background = Ui.bg,
            onBackground = Ui.fg,
            surface = Ui.bg,
            onSurface = Ui.fg,
            surfaceVariant = Ui.card,
            onSurfaceVariant = Ui.dim,
            surfaceContainer = Ui.card,
            surfaceContainerHigh = Ui.raised,
            surfaceContainerHighest = Ui.raised,
            secondaryContainer = Ui.raised,
            onSecondaryContainer = Ui.fg,
            outline = Ui.line,
            outlineVariant = Ui.line,
            error = Ui.red,
        ),
        typography = Typography(
            displayLarge = t.displayLarge.poppins(), displayMedium = t.displayMedium.poppins(),
            displaySmall = t.displaySmall.poppins(), headlineLarge = t.headlineLarge.poppins(),
            headlineMedium = t.headlineMedium.poppins(), headlineSmall = t.headlineSmall.poppins(),
            titleLarge = t.titleLarge.poppins(), titleMedium = t.titleMedium.poppins(),
            titleSmall = t.titleSmall.poppins(), bodyLarge = t.bodyLarge.poppins(),
            bodyMedium = t.bodyMedium.poppins(), bodySmall = t.bodySmall.poppins(),
            labelLarge = t.labelLarge.poppins(), labelMedium = t.labelMedium.poppins(),
            labelSmall = t.labelSmall.poppins(),
        ),
        content = content,
    )
}
