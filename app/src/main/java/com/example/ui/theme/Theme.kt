package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = M3DarkPurplePrimary,
    secondary = M3DarkPurpleSecondary,
    tertiary = Color(0xFFEFB8C8),
    background = M3DarkBackground,
    surface = M3DarkSurface,
    surfaceVariant = M3DarkSurfaceVariant,
    onPrimary = Color(0xFF381E72),
    onSecondary = M3DarkTextPrimary,
    onTertiary = Color(0xFF492532),
    onBackground = M3DarkTextPrimary,
    onSurface = M3DarkTextPrimary,
    onSurfaceVariant = M3DarkTextSecondary,
    outline = M3DarkBorderColor
)

private val LightColorScheme = lightColorScheme(
    primary = M3PurplePrimary,
    secondary = M3PurpleSecondary,
    tertiary = M3PurpleTertiary,
    background = M3PurpleBackground,
    surface = M3PurpleSurface,
    surfaceVariant = M3PurpleSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = M3ActiveIndicator,
    onTertiary = Color.White,
    onBackground = M3TextPrimary,
    onSurface = M3TextPrimary,
    onSurfaceVariant = M3TextSecondary,
    outline = M3BorderColor,
    outlineVariant = M3DividerColor
)

@Composable
fun PicocaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We can allow dynamic colors on Android 12+ or use our customized theme by default to ensure cute branding
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// For backwards-compatibility with some generated files or tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    PicocaseTheme(darkTheme = darkTheme, content = content)
}
