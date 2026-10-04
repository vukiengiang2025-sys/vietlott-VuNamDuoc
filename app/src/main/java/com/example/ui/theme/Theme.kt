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
    primary = Color(0xFFFF5252),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF8E0000),
    onPrimaryContainer = Color(0xFFFFCDD2),
    secondary = GoldSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5D4037),
    tertiary = Color(0xFF64B5F6),
    background = SurfaceDark,
    surface = CardBgDark,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

private val LightColorScheme = lightColorScheme(
    primary = RedPrimary,
    onPrimary = Color.White,
    primaryContainer = RedLight,
    onPrimaryContainer = RedDark,
    secondary = GoldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF8E1),
    tertiary = NavyTertiary,
    background = SurfaceLight,
    surface = CardBgLight,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF616161)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
