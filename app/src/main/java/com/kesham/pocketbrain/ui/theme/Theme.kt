package com.kesham.pocketbrain.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ClayColorScheme = lightColorScheme(
    primary = ClayPrimary,
    onPrimary = ClayTextPrimary,
    secondary = ClayPrimary,
    onSecondary = ClayTextPrimary,
    background = ClaySurface,
    onBackground = ClayTextPrimary,
    surface = ClaySurface,
    onSurface = ClayTextPrimary,
    surfaceVariant = ClaySurface,
    onSurfaceVariant = ClayTextSecondary,
    error = ClayAlert,
)

// The clay palette is a fixed light pastel brand look, so it does not follow the system dark theme.
@Composable
fun PocketBrainTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ClaySurface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = ClayColorScheme,
        typography = Typography,
        content = content
    )
}
