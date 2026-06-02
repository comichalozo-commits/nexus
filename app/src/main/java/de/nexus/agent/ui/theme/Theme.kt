package de.nexus.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = NexusPrimary,
    onPrimary = NexusOnPrimary,
    primaryContainer = NexusPrimaryContainer,
    onPrimaryContainer = NexusOnPrimaryContainer,
    secondary = NexusSecondary,
    onSecondary = NexusOnSecondary,
    secondaryContainer = NexusSecondaryContainer,
    onSecondaryContainer = NexusOnSecondaryContainer,
    tertiary = NexusTertiary,
    onTertiary = NexusOnTertiary,
    tertiaryContainer = NexusTertiaryContainer,
    onTertiaryContainer = NexusOnTertiaryContainer,
    error = NexusError,
    onError = NexusOnError,
    errorContainer = NexusErrorContainer,
    onErrorContainer = NexusOnErrorContainer,
    background = NexusBackgroundLight,
    onBackground = NexusOnBackgroundLight,
    surface = NexusSurfaceLight,
    onSurface = NexusOnSurfaceLight,
    surfaceVariant = NexusSurfaceVariantLight,
    onSurfaceVariant = NexusOnSurfaceVariantLight,
    outline = NexusOutlineLight,
    outlineVariant = NexusOutlineVariantLight,
    surfaceTint = NexusSurfaceTintLight
)

private val DarkColorScheme = darkColorScheme(
    primary = NexusDarkPrimary,
    onPrimary = NexusDarkOnPrimary,
    primaryContainer = NexusDarkPrimaryContainer,
    onPrimaryContainer = NexusDarkOnPrimaryContainer,
    secondary = NexusDarkSecondary,
    onSecondary = NexusDarkOnSecondary,
    secondaryContainer = NexusDarkSecondaryContainer,
    onSecondaryContainer = NexusDarkOnSecondaryContainer,
    tertiary = NexusDarkTertiary,
    onTertiary = NexusDarkOnTertiary,
    tertiaryContainer = NexusDarkTertiaryContainer,
    onTertiaryContainer = NexusDarkOnTertiaryContainer,
    error = NexusDarkError,
    onError = NexusDarkOnError,
    errorContainer = NexusDarkErrorContainer,
    onErrorContainer = NexusDarkOnErrorContainer,
    background = NexusBackgroundDark,
    onBackground = NexusOnBackgroundDark,
    surface = NexusSurfaceDark,
    onSurface = NexusOnSurfaceDark,
    surfaceVariant = NexusSurfaceVariantDark,
    onSurfaceVariant = NexusOnSurfaceVariantDark,
    outline = NexusOutlineDark,
    outlineVariant = NexusOutlineVariantDark,
    surfaceTint = NexusSurfaceTintDark
)

@Composable
fun NexusAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NexusTypography,
        shapes = NexusShapes,
        content = content
    )
}
