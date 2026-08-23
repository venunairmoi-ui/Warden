package com.venunair.warden.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = WardenPrimary,
    onPrimary = WardenOnPrimary,
    primaryContainer = WardenPrimaryContainer,
    onPrimaryContainer = WardenOnPrimaryContainer,
    secondary = WardenSecondary,
    onSecondary = WardenOnSecondary,
    secondaryContainer = WardenSecondaryContainer,
    onSecondaryContainer = WardenOnSecondaryContainer,
    tertiary = WardenTertiary,
    onTertiary = WardenOnTertiary,
    tertiaryContainer = WardenTertiaryContainer,
    onTertiaryContainer = WardenOnTertiaryContainer,
    background = WardenBackground,
    onBackground = WardenOnBackground,
    surface = WardenSurface,
    onSurface = WardenOnSurface,
    surfaceVariant = WardenSurfaceVariant,
    onSurfaceVariant = WardenOnSurfaceVariant,
    outline = WardenOutline,
    error = WardenError,
    onError = WardenOnError,
    errorContainer = WardenErrorContainer,
    onErrorContainer = WardenOnErrorContainer
)

private val DarkColors = darkColorScheme(
    primary = WardenPrimaryDark,
    onPrimary = WardenOnPrimaryDark,
    primaryContainer = WardenPrimaryContainerDark,
    onPrimaryContainer = WardenOnPrimaryContainerDark,
    secondary = WardenSecondaryDark,
    onSecondary = WardenOnSecondaryDark,
    secondaryContainer = WardenSecondaryContainerDark,
    onSecondaryContainer = WardenOnSecondaryContainerDark,
    tertiary = WardenTertiaryDark,
    onTertiary = WardenOnTertiaryDark,
    tertiaryContainer = WardenTertiaryContainerDark,
    onTertiaryContainer = WardenOnTertiaryContainerDark,
    background = WardenBackgroundDark,
    onBackground = WardenOnBackgroundDark,
    surface = WardenSurfaceDark,
    onSurface = WardenOnSurfaceDark,
    surfaceVariant = WardenSurfaceVariantDark,
    onSurfaceVariant = WardenOnSurfaceVariantDark,
    outline = WardenOutlineDark,
    error = WardenErrorDark,
    onError = WardenOnErrorDark,
    errorContainer = WardenErrorContainerDark,
    onErrorContainer = WardenOnErrorContainerDark
)

@Composable
fun WardenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You dynamic color (Android 12+) would derive every color from
    // the user's wallpaper and completely override this palette — defaulting
    // it off so the intentional brand colors above actually render. The
    // capability is kept (not deleted) in case you'd rather each user get a
    // wallpaper-matched app than a consistent brand look later — that's a
    // real product choice to revisit, not an oversight to fix.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WardenTypography,
        shapes = WardenShapes,
        content = content,
    )
}
