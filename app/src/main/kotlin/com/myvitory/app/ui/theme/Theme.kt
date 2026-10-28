package com.myvitory.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myvitory.app.R

/** Tokens da prancheta "Sistema visual". */
object Palette {
    val Ground = Color(0xFF0E0F10)
    val Surface = Color(0xFF18191B)
    val SurfaceHigh = Color(0xFF232427)
    val Line = Color(0xFF2C2E31)
    val LineStrong = Color(0xFF34363A)
    val Text = Color(0xFFF3F2EE)
    val TextMuted = Color(0xFFA3A39E)
    val TextFaint = Color(0xFF8C8C87)
    val Lime = Color(0xFFCDF24B)
    val Orange = Color(0xFFFF9A5C)
    val OrangeSoft = Color(0xFF2A1E16)
    val Blue = Color(0xFF8CB8FF)
    val Neutral = Color(0xFF4A4C50)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private val colors = darkColorScheme(
    primary = Palette.Lime,
    onPrimary = Palette.Ground,
    secondary = Palette.Blue,
    onSecondary = Palette.Ground,
    tertiary = Palette.Orange,
    onTertiary = Palette.Ground,
    background = Palette.Ground,
    onBackground = Palette.Text,
    surface = Palette.Ground,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Surface,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Ground,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.LineStrong,
    outlineVariant = Palette.Line,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Ground,
    inversePrimary = Palette.Lime,
)

private val base = TextStyle(fontFamily = Barlow, color = Color.Unspecified)

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp),
    headlineSmall = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp),
    titleLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = base.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.3.sp),
)

@Composable
fun MyVitoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
