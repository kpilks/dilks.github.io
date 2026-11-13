package uk.co.ccooper.predictor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1B6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F1C4),
    onPrimaryContainer = Color(0xFF00210D),
    secondary = Color(0xFF4F6353),
    secondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFFB8860B),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF6F8F4),
    surface = Color(0xFFF6F8F4),
    surfaceContainer = Color(0xFFEBEFE8),
    surfaceContainerHigh = Color(0xFFE5E9E2),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9AD5A9),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005228),
    onPrimaryContainer = Color(0xFFB6F1C4),
    secondary = Color(0xFFB6CCB8),
    secondaryContainer = Color(0xFF384B3C),
    tertiary = Color(0xFFF2C14E),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF111511),
    surface = Color(0xFF111511),
    surfaceContainer = Color(0xFF1D211D),
    surfaceContainerHigh = Color(0xFF272B27),
)

/** Colours for right / wrong / live markers that work in both themes. */
object Marks {
    val correct @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FD08C) else Color(0xFF1E8E3E)
    val wrong @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFC62828)
    val live @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF6E6E) else Color(0xFFD32F2F)
}

@Composable
fun PredictorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Theme with an explicit mode (used by screenshot tests). */
@Composable
fun PredictorThemeFor(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
