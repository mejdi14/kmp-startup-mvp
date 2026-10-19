package com.mejdi14.kmpstartupmvp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Seed of Flutter's `ColorScheme.fromSeed(seedColor: Colors.deepPurple)` (§1.9). */
val DeepPurpleSeed = Color(0xFF673AB7)

/**
 * Light Material3 scheme for the deepPurple seed, defined locally (no
 * material-color-utilities dependency). Values are a hand-written tonal-spot
 * approximation of Flutter's `fromSeed` output, not a bit-exact generation;
 * regenerate from [DeepPurpleSeed] if pixel parity with Flutter ever matters.
 * Light only: Flutter defines no dark theme (§1.9).
 */
val AppColorScheme = lightColorScheme(
    primary = Color(0xFF675788),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEDCFF),
    onPrimaryContainer = Color(0xFF220F40),
    secondary = Color(0xFF645B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBDDF9),
    onSecondaryContainer = Color(0xFF1F182B),
    tertiary = Color(0xFF7D516E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8F0),
    onTertiaryContainer = Color(0xFF310C29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEF7FE),
    onBackground = Color(0xFF1D1B1F),
    surface = Color(0xFFFEF7FE),
    onSurface = Color(0xFF1D1B1F),
    surfaceVariant = Color(0xFFE8E0EB),
    onSurfaceVariant = Color(0xFF4A454E),
    outline = Color(0xFF7B757F),
    outlineVariant = Color(0xFFCCC4CF),
    inverseSurface = Color(0xFF322F34),
    inverseOnSurface = Color(0xFFF5EFF5),
    inversePrimary = Color(0xFFD1BCF9),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColorScheme, content = content)
}
