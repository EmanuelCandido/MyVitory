package com.myvitory.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.myvitory.app.perf.MotionLevel
import kotlinx.coroutines.delay

/**
 * Tokens de movimento (os mesmos de web/src/styles.css). Regras: só escala, deslocamento, giro e opacidade
 * animam, sempre em graphicsLayer (fase de desenho: não recompõe nem refaz layout); nada passa de 400 ms;
 * animação nunca bloqueia toque.
 */
object Motion {
    const val Tap = 90
    const val Short = 160
    const val Medium = 240
    const val Long = 360

    val EaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** Mola do carimbo: passa um pouco do ponto e assenta, como adesivo batido na mesa. */
    val Stamp = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)
}

/** Nível de movimento do aparelho; [MyVitoryTheme] preenche com DevicePerformance. */
val LocalMotion = staticCompositionLocalOf { MotionLevel.Full }

/** Retorno de toque: afunda para 96% enquanto pressionado. Fica no nível leve; some só no desligado. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.96f): Modifier {
    if (LocalMotion.current == MotionLevel.Off) return this
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, tween(Motion.Tap, easing = Motion.EaseOut), label = "pressScale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Carimbos que já tocaram nesta execução do app: entrar de novo na aba não repete a animação. */
private object Stamped {
    val keys = mutableSetOf<String>()
}

/**
 * Entrada de carimbo (escala 1,35 → 1 com giro de −8°), [index] × 70 ms depois das anteriores.
 * Só no nível completo e uma vez por [key] por execução.
 */
@Composable
fun Modifier.stampIn(key: String, index: Int = 0): Modifier {
    if (LocalMotion.current != MotionLevel.Full) return this
    val fresh = remember(key) { Stamped.keys.add(key) }
    if (!fresh) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(index * 70L)
        progress.animateTo(1f, Motion.Stamp)
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        val s = 1.35f - 0.35f * p
        scaleX = s
        scaleY = s
        rotationZ = -8f * (1f - p)
    }
}

/** Balanço único do selo de alerta ao aparecer (só no completo). */
@Composable
fun Modifier.wiggleOnce(key: Any): Modifier {
    if (LocalMotion.current != MotionLevel.Full) return this
    val angle = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        for (target in listOf(10f, -14f, 4f, 0f)) angle.animateTo(target, tween(110, easing = Motion.EaseOut))
    }
    return graphicsLayer { rotationZ = angle.value }
}
