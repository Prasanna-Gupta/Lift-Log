package com.asur.gymapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Coral = Color(0xFFFF7A5C)
private val CoralDark = Color(0xFFE85F3F)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceVariant = Color(0xFF2A2A2A)
private val OnDark = Color(0xFFF2F2F2)
private val OnDarkVariant = Color(0xFFAAAAAA)
private val ErrorRed = Color(0xFFE85D5D)

private val GymAppDarkColors = darkColorScheme(
    primary = Coral,
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = CoralDark,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Coral,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkVariant,
    error = ErrorRed,
    onError = Color(0xFF1A1A1A)
)

private val GymAppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = OnDarkVariant),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, letterSpacing = (-1).sp)
)

private val GymAppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GymAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GymAppDarkColors,
        typography = GymAppTypography,
        shapes = GymAppShapes,
        content = content
    )
}