package cn.zjl.datacollector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BlueLight,
    secondary = BlueAccent,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = PageBackground,
    surface = SurfaceCard,
    surfaceVariant = SurfaceCardStrong,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = Error,
    outline = androidx.compose.ui.graphics.Color(0xFFD8D1C5)  // divider 色
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueLight,
    onPrimary = BluePrimaryDark,
    secondary = BlueAccent,
    background = androidx.compose.ui.graphics.Color(0xFF1A1C1E),
    surface = androidx.compose.ui.graphics.Color(0xFF2D3132),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE2E2E6),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE2E2E6),
)

@Composable
fun DataCollectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DataCollectorTypography,
        content = content
    )
}
