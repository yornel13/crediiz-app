package com.project.vortex.callsagent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,

    secondary = Slate700,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Slate900,

    tertiary = Coral500,
    onTertiary = Color.White,
    tertiaryContainer = Coral200,
    onTertiaryContainer = Slate900,

    error = Rose600,
    onError = Color.White,
    errorContainer = Rose100,
    onErrorContainer = Slate900,

    background = OffWhite,
    onBackground = Slate900,

    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Slate50,
    surfaceContainer = Slate100,
    surfaceContainerHigh = Slate200,
    surfaceContainerHighest = Slate300,

    outline = Slate300,
    outlineVariant = Slate200,
)

private val DarkScheme = darkColorScheme(
    primary = Teal500,
    onPrimary = Slate900,
    primaryContainer = Teal900,
    onPrimaryContainer = Teal100,

    secondary = Slate300,
    onSecondary = Slate900,
    secondaryContainer = Slate700,
    onSecondaryContainer = Slate100,

    tertiary = Coral500,
    onTertiary = Slate900,
    tertiaryContainer = Coral500.copy(alpha = 0.3f),
    onTertiaryContainer = Coral200,

    error = Rose600,
    onError = Color.White,
    errorContainer = Rose600.copy(alpha = 0.3f),
    onErrorContainer = Rose100,

    background = DeepInk,
    onBackground = Slate100,

    surface = DeepInkSurface,
    onSurface = Slate100,
    surfaceVariant = DeepInkSurfaceHigh,
    onSurfaceVariant = Slate300,
    surfaceContainerLowest = DeepInk,
    surfaceContainerLow = DeepInkSurface,
    surfaceContainer = DeepInkSurfaceHigh,
    surfaceContainerHigh = Slate700,
    surfaceContainerHighest = Slate700.copy(alpha = 0.6f),

    outline = Slate500,
    outlineVariant = Slate700,
)

@Composable
fun CallsAgendsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
