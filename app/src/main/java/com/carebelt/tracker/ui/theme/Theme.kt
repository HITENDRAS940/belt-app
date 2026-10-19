package com.carebelt.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CareBeltColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),          // Deep Medical Teal
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF1D4ED8),        // Navigation Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF7C3AED),         // Haptic / Alert Accent
    error = Color(0xFFDC2626),            // Emergency / Geofence Breach Red
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun CareBeltTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CareBeltColorScheme,
        content = content
    )
}
