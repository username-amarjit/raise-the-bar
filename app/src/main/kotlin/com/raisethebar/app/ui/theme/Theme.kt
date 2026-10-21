package com.raisethebar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Follows the phone's light or dark setting. */
@Composable
fun RtbTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkRtbColors else LightRtbColors
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = c.ink,
                onPrimary = c.bg,
                secondary = c.pull,
                onSecondary = c.onAccent,
                tertiary = c.gold,
                background = c.bg,
                onBackground = c.ink,
                surface = c.surface,
                onSurface = c.ink,
                surfaceVariant = c.surface2,
                onSurfaceVariant = c.ink2,
                surfaceContainer = c.surface,
                surfaceContainerHigh = c.surface,
                surfaceContainerHighest = c.surface2,
                outline = c.line,
                outlineVariant = c.line,
                error = c.push,
                onError = c.onAccent,
            )
        } else {
            lightColorScheme(
                primary = c.ink,
                onPrimary = c.bg,
                secondary = c.pull,
                onSecondary = c.onAccent,
                tertiary = c.gold,
                background = c.bg,
                onBackground = c.ink,
                surface = c.surface,
                onSurface = c.ink,
                surfaceVariant = c.surface2,
                onSurfaceVariant = c.ink2,
                surfaceContainer = c.surface,
                surfaceContainerHigh = c.surface,
                surfaceContainerHighest = c.surface2,
                outline = c.line,
                outlineVariant = c.line,
                error = c.push,
                onError = c.onAccent,
            )
        }
    CompositionLocalProvider(LocalRtbColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = RtbTypography, content = content)
    }
}

object Rtb {
    val colors: RtbColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRtbColors.current
}
