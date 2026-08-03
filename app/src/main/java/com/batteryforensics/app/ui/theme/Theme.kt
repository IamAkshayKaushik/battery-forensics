package com.batteryforensics.app.ui.theme

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

private val ForestGreen = Color(0xFF1B7A4E)
private val Mist = Color(0xFFE7F2EC)
private val Ink = Color(0xFF0B1F17)
private val Amber = Color(0xFFC47B2C)

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    secondary = Amber,
    background = Mist,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CFFB2),
    onPrimary = Ink,
    secondary = Color(0xFFE0A45A),
    background = Color(0xFF07140F),
    surface = Color(0xFF0F241C),
    onBackground = Color(0xFFE7F2EC),
    onSurface = Color(0xFFE7F2EC),
)

@Composable
fun BatteryForensicsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
