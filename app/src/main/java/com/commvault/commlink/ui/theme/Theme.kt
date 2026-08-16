package com.commvault.commlink.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CommvaultLightScheme = lightColorScheme(
    primary            = CommvaultPink,
    onPrimary          = LightSurface,
    secondary          = CommvaultNavy,
    onSecondary        = LightSurface,
    background         = LightBg,
    onBackground       = TextPrimary,
    surface            = LightSurface,
    onSurface          = TextPrimary,
    surfaceVariant     = LightSurfaceAlt,
    onSurfaceVariant   = TextSecondary,
    outline            = BorderColor,
    error              = ErrorRed,
    onError            = LightSurface
)

@Composable
fun CommLinkTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = LightBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = CommvaultLightScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
