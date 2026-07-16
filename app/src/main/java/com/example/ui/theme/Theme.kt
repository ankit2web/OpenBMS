package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyberGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF003816), // Dark green container
    onPrimaryContainer = CyberGreen,
    secondary = CyberBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00334E), // Dark blue container
    onSecondaryContainer = CyberBlue,
    tertiary = CyberOrange,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = CyberRed,
    outline = Color(0xFF334155), // Slate 700 border for dark mode
    outlineVariant = Color(0xFF1E293B) // Slate 800 border for dark mode
)

private val LightColorScheme = lightColorScheme(
    primary = HighDensityAccent,
    onPrimary = Color.White,
    primaryContainer = HighDensityButtonContainerBg,
    onPrimaryContainer = HighDensityButtonContainerText,
    secondary = HighDensityAccent, // Use HighDensityAccent so secondary elements (like DEV_UX_LAB) are perfectly visible
    onSecondary = Color.White,
    secondaryContainer = HighDensityButtonContainerBg,
    onSecondaryContainer = HighDensityButtonContainerText,
    tertiary = HighDensityButtonContainerText,
    background = HighDensityBg,
    surface = LightSurface,
    onBackground = HighDensityTextPrimary,
    onSurface = HighDensityTextPrimary,
    surfaceVariant = LightSurface, // Set to pure white so all cards using surfaceVariant pop against the slate background
    onSurfaceVariant = HighDensityTextSecondary,
    error = Color(0xFFEF4444),
    outline = Color(0xFF94A3B8), // Slate 400 for highly visible outlines
    outlineVariant = Color(0xFFCBD5E1) // Slate 300 for crisp borders
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Set false to maintain our gorgeous cyberpunk styling by default
    content: @Composable () -> Unit,
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
