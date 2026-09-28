package com.coldai.assistant.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Ice = Color(0xFF7ED0EA)
val IceDeep = Color(0xFF1A6B88)
val Night = Color(0xFF070B12)
val NightCard = Color(0xFF101826)
val Frost = Color(0xFFE8F4FA)

private val DarkColors = darkColorScheme(
    primary = Ice,
    onPrimary = Color(0xFF06202A),
    primaryContainer = Color(0xFF163044),
    onPrimaryContainer = Frost,
    secondary = Color(0xFF9BB8C8),
    onSecondary = Night,
    background = Night,
    onBackground = Frost,
    surface = Night,
    onSurface = Frost,
    surfaceVariant = NightCard,
    onSurfaceVariant = Color(0xFFB7C9D4),
    outline = Color(0xFF2A3D4E),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = IceDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEF6),
    onPrimaryContainer = Color(0xFF06202A),
    secondary = Color(0xFF3E5A68),
    background = Color(0xFFF4F8FB),
    onBackground = Color(0xFF102028),
    surface = Color(0xFFF4F8FB),
    onSurface = Color(0xFF102028),
    surfaceVariant = Color(0xFFE4EEF3),
    onSurfaceVariant = Color(0xFF3A5160),
    outline = Color(0xFFC5D4DC),
    error = Color(0xFFB3261E)
)

private val ColdTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun ColdTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = ColdTypography, content = content)
}
