package com.example.studenttimetotalnote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PaperAndInkColors = lightColorScheme(
    primary = Ink,
    onPrimary = White,
    primaryContainer = Ink,
    onPrimaryContainer = White,
    secondary = Cobalt,
    onSecondary = White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = MenuPaper,
    onSurfaceVariant = MutedInk,
    outline = MutedInk,
    outlineVariant = Hairline,
)

/**
 * The approved mock-up is deliberately a single light paper canvas. Keeping the palette
 * independent from device dark mode is part of matching that visual baseline.
 */
@Composable
fun StudentTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperAndInkColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
