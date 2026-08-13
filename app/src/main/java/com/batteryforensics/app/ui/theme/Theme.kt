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

/** Forensic brand — forest evidence + amber severity. Not purple-AI defaults. */
private val ForestGreen = Color(0xFF1B7A4E)
private val ForestDeep = Color(0xFF0F4D32)
private val Mist = Color(0xFFE7F2EC)
private val MistSoft = Color(0xFFF3F8F5)
private val Ink = Color(0xFF0B1F17)
private val Amber = Color(0xFFC47B2C)
private val AmberSoft = Color(0xFFF3E0C8)
private val Teal = Color(0xFF1A6B6B)
private val Critical = Color(0xFFB3261E)
private val OutlineSoft = Color(0xFFB7C9BF)

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EAD8),
    onPrimaryContainer = ForestDeep,
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = AmberSoft,
    onSecondaryContainer = Color(0xFF3D2408),
    tertiary = Teal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6E8E8),
    onTertiaryContainer = Color(0xFF0A3333),
    error = Critical,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Mist,
    onBackground = Ink,
    surface = MistSoft,
    onSurface = Ink,
    surfaceVariant = Color(0xFFD5E5DC),
    onSurfaceVariant = Color(0xFF3D4F45),
    outline = OutlineSoft,
    outlineVariant = Color(0xFFD0DDD5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = MistSoft,
    surfaceContainer = Color(0xFFDCEBE3),
    surfaceContainerHigh = Color(0xFFD0E3D8),
    surfaceContainerHighest = Color(0xFFC4DBCE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CFFB2),
    onPrimary = Ink,
    primaryContainer = ForestDeep,
    onPrimaryContainer = Color(0xFFC8EAD8),
    secondary = Color(0xFFE0A45A),
    onSecondary = Color(0xFF2A1804),
    secondaryContainer = Color(0xFF5A3A14),
    onSecondaryContainer = AmberSoft,
    tertiary = Color(0xFF7ED4D4),
    onTertiary = Color(0xFF003333),
    tertiaryContainer = Color(0xFF0F4545),
    onTertiaryContainer = Color(0xFFC6E8E8),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF07140F),
    onBackground = Color(0xFFE7F2EC),
    surface = Color(0xFF0F241C),
    onSurface = Color(0xFFE7F2EC),
    surfaceVariant = Color(0xFF1A3328),
    onSurfaceVariant = Color(0xFFB7C9BF),
    outline = Color(0xFF6B8074),
    outlineVariant = Color(0xFF2A4036),
    surfaceContainerLowest = Color(0xFF050E0A),
    surfaceContainerLow = Color(0xFF0C1C15),
    surfaceContainer = Color(0xFF122820),
    surfaceContainerHigh = Color(0xFF183028),
    surfaceContainerHighest = Color(0xFF1F3A30),
)

@Composable
fun BatteryForensicsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Off by default so Forest/Amber brand ships; Settings can opt into Material You later. */
    dynamicColor: Boolean = false,
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
