package com.asur.gymapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.Font
import liftlog.shared.generated.resources.Res
import liftlog.shared.generated.resources.inter_regular
import liftlog.shared.generated.resources.inter_medium
import liftlog.shared.generated.resources.inter_semibold
import liftlog.shared.generated.resources.inter_bold

private val Coral = Color(0xFFFF7A5C)
private val CoralDark = Color(0xFFE85F3F)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1A1A1A)
private val DarkSurfaceVariant = Color(0xFF2A2A2A)
private val OnDark = Color(0xFFF2F2F2)
private val OnDarkVariant = Color(0xFF8A8A8A)
private val ErrorRed = Color(0xFFE85D5D)

object AppColors {
    val NavBar = Color(0xFF1E1E1E)
    val TextTertiary = Color(0xFF6B6B6B)
    val Divider = Color(0xFF232323)
    val Destructive = Color(0xFFC4564C)
    val HeatEmpty = Color(0xFF232323)
    val HeatLow = Color(0xFF5C3227)
    val HeatMid = Color(0xFFA34A33)
    val HeatFull = Color(0xFFFF7A5C)
}

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

private val GymAppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun interFontFamily() = FontFamily(
    Font(Res.font.inter_regular, weight = FontWeight.Normal),
    Font(Res.font.inter_medium, weight = FontWeight.Medium),
    Font(Res.font.inter_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.inter_bold, weight = FontWeight.Bold)
)

@Composable
fun GymAppTheme(content: @Composable () -> Unit) {
    val interFamily = interFontFamily()
    val typography = Typography(
        headlineMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.5).sp, color = Coral),
        titleLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        bodyLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = OnDarkVariant),
        labelMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
        displayLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp, letterSpacing = (-1).sp),
        labelSmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.9.sp, color = AppColors.TextTertiary),
    )

    MaterialTheme(
        colorScheme = GymAppDarkColors,
        typography = typography,
        shapes = GymAppShapes
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}

@Composable
fun BoxScope.BottomFadeOverlay() {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(256.dp)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color.Transparent,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    )
}