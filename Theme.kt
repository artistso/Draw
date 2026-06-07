package com.animationstudio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Brand colors inspired by modern design tools
val DarkCharcoal = Color(0xFF1A1A2E)
val DeepSlate = Color(0xFF16213E)
val MidnightBlue = Color(0xFF0F3460)
val VibrantPurple = Color(0xFF7B2FF7)
val ElectricIndigo = Color(0xFF6C63FF)
val CyanAccent = Color(0xFF00D9FF)
val CoralPink = Color(0xFFFF6B6B)
val WarmOrange = Color(0xFFFF8C42)
val MintGreen = Color(0xFF4ECDC4)
val SoftWhite = Color(0xFFF7F7FF)
val LightGray = Color(0xFFE8E8ED)
val MediumGray = Color(0xFF9E9EAD)
val DarkGray = Color(0xFF2D2D3F)

// Light theme colors
private val LightColorScheme = lightColorScheme(
    primary = VibrantPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E0FF),
    onPrimaryContainer = Color(0xFF1F006B),
    secondary = CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF5FF),
    onSecondaryContainer = Color(0xFF003545),
    tertiary = CoralPink,
    onTertiary = Color.White,
    background = SoftWhite,
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = LightGray,
    onSurfaceVariant = MediumGray,
    outline = MediumGray
)

// Dark theme colors
private val DarkColorScheme = darkColorScheme(
    primary = ElectricIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = CyanAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004D64),
    onSecondaryContainer = Color(0xFFCCF5FF),
    tertiary = CoralPink,
    onTertiary = Color.Black,
    background = DarkCharcoal,
    onBackground = SoftWhite,
    surface = DeepSlate,
    onSurface = SoftWhite,
    surfaceVariant = DarkGray,
    onSurfaceVariant = LightGray,
    outline = MediumGray
)

@Composable
fun AnimationStudioTheme(
    windowSizeClass: WindowSizeClass = rememberWindowSizeClass(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Enable edge-to-edge rendering
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * Window size class for adaptive layouts on large screens like Tab S10+
 */
data class WindowSizeClass(
    val widthClass: WidthClass = WidthClass.COMPACT,
    val heightClass: HeightClass = HeightClass.COMPACT,
    val isLargeScreen: Boolean = false,
    val isTablet: Boolean = false
)

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }
enum class HeightClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp

    val widthClass = when {
        screenWidthDp < 600 -> WidthClass.COMPACT
        screenWidthDp < 840 -> WidthClass.MEDIUM
        else -> WidthClass.EXPANDED
    }

    val heightClass = when {
        screenHeightDp < 480 -> HeightClass.COMPACT
        screenHeightDp < 900 -> HeightClass.MEDIUM
        else -> HeightClass.EXPANDED
    }

    val isLargeScreen = screenWidthDp >= 960 || screenHeightDp >= 960

    return WindowSizeClass(
        widthClass = widthClass,
        heightClass = heightClass,
        isLargeScreen = isLargeScreen,
        isTablet = isLargeScreen
    )
}
