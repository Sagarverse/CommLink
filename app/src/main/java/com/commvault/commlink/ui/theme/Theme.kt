package com.commvault.commlink.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CommLinkTheme(
    themeColorHex: StateFlow<String>? = null,
    content: @Composable () -> Unit
) {
    val colorHex = themeColorHex?.collectAsState()?.value ?: ""
    
    val primary = remember(colorHex) {
        hexToColor(colorHex) ?: PrimaryEmerald
    }
    val primaryLight = remember(primary) { deriveLightVariant(primary) }
    val primaryDark = remember(primary) { deriveDarkVariant(primary) }
    val primarySoft = remember(primary) { deriveSoftVariant(primary) }

    val colorScheme = remember(primary) {
        lightColorScheme(
            primary            = primary,
            onPrimary          = CardSurface,
            secondary          = SecondaryDark,
            onSecondary        = CardSurface,
            background         = PageBackground,
            onBackground       = SecondaryDark,
            surface            = CardSurface,
            onSurface          = SecondaryDark,
            surfaceVariant     = CardSurfaceAlt,
            onSurfaceVariant   = NeutralSlate,
            outline            = BorderLight,
            error              = ErrorRed,
            onError            = CardSurface
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PageBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    CompositionLocalProvider(
        LocalPrimaryColor provides primary,
        LocalPrimaryLight provides primaryLight,
        LocalPrimaryDark provides primaryDark,
        LocalPrimarySoft provides primarySoft
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
