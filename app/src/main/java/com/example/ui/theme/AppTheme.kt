package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EditorColors.AccentPrimary,
    secondary = EditorColors.AccentSecondary,
    background = EditorColors.BackgroundDark,
    surface = EditorColors.SurfaceDark,
    surfaceVariant = EditorColors.SurfaceVariantDark,
    onPrimary = EditorColors.TextPrimary,
    onSecondary = EditorColors.TextPrimary,
    onBackground = EditorColors.TextPrimary,
    onSurface = EditorColors.TextPrimary,
    onSurfaceVariant = EditorColors.TextSecondary,
    error = EditorColors.Error,
    outline = EditorColors.TextDisabled
)

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme, // Force dark theme for pro video editor
        typography = EditorTypography.EditorTypography,
        content = content
    )
}
