// ui/theme/Theme.kt
package com.v2ray.ang.shadowmm.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ShadowPrimary,
    onPrimary = Color.White,
    secondary = ShadowAccent,
    background = BackgroundBottom,
    surface = Color(0xFF1D1D32),
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ShadowPrimary,
    onPrimary = Color.White,
    secondary = ShadowAccent,
    background = Color(0xFFF6F2FF),
    surface = Color.White,
    onSurface = Color(0xFF1A1440)
)

@Composable
fun ShadowLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
