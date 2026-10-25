package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuraColorScheme = darkColorScheme(
    primary = AuraAmber,
    onPrimary = AuraAmberDarkText,
    primaryContainer = AuraTileTop,
    onPrimaryContainer = AuraText,
    secondary = AuraTeal,
    onSecondary = AuraBg,
    secondaryContainer = AuraPanelTop,
    onSecondaryContainer = AuraTeal,
    tertiary = AuraViolet,
    onTertiary = AuraBg,
    error = AuraRose,
    onError = AuraBg,
    background = AuraBg,
    onBackground = AuraText,
    surface = AuraSidebarBg,
    onSurface = AuraText,
    surfaceVariant = AuraPanelTop,
    onSurfaceVariant = AuraDim,
    outline = AuraLine,
    outlineVariant = AuraLineStrong
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        typography = Typography,
        content = content
    )
}
