package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LingoPrimaryLight,
    secondary = LingoSecondaryLight,
    tertiary = LingoGold,
    background = LingoBackgroundDark,
    surface = LingoSurfaceDark,
    onPrimary = LingoBackgroundDark,
    onSecondary = LingoSurfaceLight,
    onBackground = LingoBackgroundLight,
    onSurface = LingoBackgroundLight,
    error = LingoErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = LingoPrimary,
    secondary = LingoSecondary,
    tertiary = LingoGold,
    background = LingoBackgroundLight,
    surface = LingoSurfaceLight,
    onPrimary = LingoSurfaceLight,
    onSecondary = LingoBackgroundDark,
    onBackground = LingoBackgroundDark,
    onSurface = LingoBackgroundDark,
    error = LingoErrorRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors by default to preserve Lingo brand aesthetic
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
