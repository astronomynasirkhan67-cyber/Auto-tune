package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StudioColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = StudioBackground,
    primaryContainer = StudioCardElevated,
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = TextWhite,
    secondaryContainer = StudioBorder,
    onSecondaryContainer = NeonPurpleBright,
    tertiary = NeonPink,
    background = StudioBackground,
    onBackground = TextWhite,
    surface = StudioSurface,
    onSurface = TextWhite,
    surfaceVariant = StudioCard,
    onSurfaceVariant = TextLight,
    outline = StudioBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = StudioBackground.toArgb()
                window.navigationBarColor = StudioBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
