package com.myvitory.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.myvitory.app.perf.DevicePerformance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myvitory.app.R

/**
 * Sistema Adesivo (os mesmos tokens de web/src/styles.css).
 * Cada cor tem um papel fixo: Volt = faça isto (ação principal, hoje, feito) · Flame = agora/atenção (timer,
 * pendência, placa de decisão) · Paper = alternativa dentro da placa · Sky = informação, algo que mudou.
 * Texto sobre Volt, Flame e Paper é sempre Ink.
 */
object Palette {
    val Ground = Color(0xFF1A1A18)
    val Surface = Color(0xFF252523)
    val SurfaceHigh = Color(0xFF30302D)
    val Line = Color(0xFF3B3B38)
    val LineStrong = Color(0xFF4A4A46)
    val Text = Color(0xFFF4F3EE)
    val TextMuted = Color(0xFFB4B3AC)
    val TextFaint = Color(0xFF8E8D87)
    val Volt = Color(0xFFE5FF1F)
    val VoltSoft = Color(0xFF2F3510)
    val Flame = Color(0xFFFF8A3D)
    val FlameSoft = Color(0xFF3A2416)
    val Sky = Color(0xFF8CB8FF)
    val SkySoft = Color(0xFF1B2536)
    val Paper = Color(0xFFF6F5EF)
    val Ink = Color(0xFF121211)
    val Neutral = Color(0xFF55544F)
}

/** Camada de ler: listas, notas, explicações. */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/** Camada de olhar: títulos, números, rótulos e botões (JetBrains Mono, OFL; os dígitos não dançam). */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_extrabold, FontWeight.ExtraBold),
)

private val colors = darkColorScheme(
    primary = Palette.Volt,
    onPrimary = Palette.Ink,
    secondary = Palette.Sky,
    onSecondary = Palette.Ink,
    tertiary = Palette.Flame,
    onTertiary = Palette.Ink,
    background = Palette.Ground,
    onBackground = Palette.Text,
    surface = Palette.Ground,
    onSurface = Palette.Text,
    // Tom próprio, que nenhum card usa: com surfaceVariant = Surface, o texto de todo card herdava o cinza de
    // onSurfaceVariant e os títulos perdiam peso. Assim, card Surface/SurfaceHigh herda Text.
    surfaceVariant = Color(0xFF2A2A28),
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Ground,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.LineStrong,
    outlineVariant = Palette.Line,
    inverseSurface = Palette.Paper,
    inverseOnSurface = Palette.Ink,
    inversePrimary = Palette.Volt,
    error = Palette.Flame,
    onError = Palette.Ink,
)

private val base = TextStyle(fontFamily = Barlow, color = Color.Unspecified)
private val mono = TextStyle(fontFamily = Mono, color = Color.Unspecified)

/** Mono é larga: os tamanhos de título são menores que os da Barlow Condensed que ela substitui. */
private val typography = Typography(
    displaySmall = mono.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-0.6).sp),
    headlineMedium = mono.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    headlineSmall = mono.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp),
    titleLarge = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp),
    titleMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = mono.copy(fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
    labelMedium = base.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = mono.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 1.sp),
)

@Composable
fun MyVitoryTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motion = remember { DevicePerformance.motionLevel(context) }
    CompositionLocalProvider(LocalMotion provides motion) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
