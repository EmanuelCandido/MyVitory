package com.myvitory.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.myvitory.app.perf.MotionLevel
import com.myvitory.app.ui.theme.LocalMotion
import com.myvitory.app.ui.theme.Motion
import com.myvitory.app.ui.theme.pressScale
import com.myvitory.app.ui.theme.stampIn
import com.myvitory.app.ui.theme.wiggleOnce
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvitory.app.R
import com.myvitory.app.domain.DayState
import com.myvitory.app.ui.theme.Palette

/*
 * Peças do Sistema Adesivo. Forma diz o papel: pílula (redonda) recebe toque, etiqueta (canto chanfrado) só mostra dado.
 * Uma pílula Volt por tela; alternativas da mesma pergunta ficam numa placa Flame, em Paper.
 */

/** Canto chanfrado: forma de dado (etiquetas, chips de status, códigos de treino). */
val TicketShape = CutCornerShape(4.dp)

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Botão principal: um por tela, Volt, 58 dp de altura, rótulo mono em caixa alta (verbo + objeto). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pressScale(interaction).heightIn(min = 58.dp),
        interactionSource = interaction,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Volt,
            contentColor = Palette.Ink,
            disabledContainerColor = Palette.SurfaceHigh,
            disabledContentColor = Palette.TextFaint,
        ),
    ) {
        if (icon != null) {
            AppIcon(icon, null, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
    }
}

/** Alternativa dentro da placa de decisão: pílula Paper, mesma forma da principal e menos peso. */
@Composable
fun PaperButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.pressScale(interaction).heightIn(min = 52.dp),
        interactionSource = interaction,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Paper,
            contentColor = Palette.Ink,
            disabledContainerColor = Palette.SurfaceHigh,
            disabledContentColor = Palette.TextFaint,
        ),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp), textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Ação de apoio, fora de uma decisão (Ver treino, Adicionar série): contorno de 2 dp. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(interaction).heightIn(min = 48.dp),
        interactionSource = interaction,
        shape = CircleShape,
        border = BorderStroke(2.dp, Palette.LineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), textAlign = TextAlign.Center)
    }
}

/**
 * Placa de decisão: as respostas de uma mesma pergunta juntas num bloco Flame, como a moldura da referência.
 * Alternativas em [PaperButton], a recomendada em [PrimaryButton], por último (perto do polegar). No máximo uma por tela.
 */
@Composable
fun DecisionPlate(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(Palette.Flame)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

enum class TicketTone(val background: Color, val content: Color) {
    Flame(Palette.Flame, Palette.Ink),
    Volt(Palette.Volt, Palette.Ink),
    Paper(Palette.Paper, Palette.Ink),
    Sky(Palette.Sky, Palette.Ink),
    Dim(Palette.SurfaceHigh, Palette.Text),
}

/**
 * Etiqueta: número grande e unidade pequena ("05 EXERCÍCIOS"), como cada unidade do contador da referência.
 * Só mostra dado; [description] é o que o leitor de tela fala no lugar do texto abreviado.
 */
@Composable
fun Ticket(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    tone: TicketTone = TicketTone.Flame,
    big: Boolean = false,
    description: String? = null,
    /** Carimbo de entrada (uma vez por execução por chave); [stampIndex] escalona 70 ms. */
    stampKey: String? = null,
    stampIndex: Int = 0,
    /** Valor novo entra de cima e o antigo sai por baixo (contador do descanso, progresso). */
    animateValue: Boolean = false,
) {
    val style = MaterialTheme.typography.labelLarge
    val valueStyle = style.copy(fontSize = if (big) 30.sp else 14.sp, lineHeight = if (big) 32.sp else 17.sp, letterSpacing = 0.sp)
    val unitStyle = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 12.sp else 9.5.sp, letterSpacing = 0.4.sp)
    val animated = animateValue && LocalMotion.current == MotionLevel.Full
    Row(
        modifier
            .then(if (stampKey != null) Modifier.stampIn(stampKey, stampIndex) else Modifier)
            .clip(TicketShape)
            .background(tone.background)
            .padding(horizontal = if (big) 11.dp else 8.dp, vertical = if (big) 6.dp else 5.dp)
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (animated) {
            AnimatedContent(
                targetState = value.uppercase(),
                transitionSpec = {
                    (slideInVertically(tween(Motion.Short, easing = Motion.EaseOut)) { -it } + fadeIn(tween(Motion.Short))) togetherWith
                        (slideOutVertically(tween(Motion.Short, easing = Motion.EaseOut)) { it } + fadeOut(tween(Motion.Tap))) using
                        SizeTransform(clip = true)
                },
                label = "ticketValue",
                modifier = Modifier.align(Alignment.Bottom),
            ) { text -> Text(text, style = valueStyle, color = tone.content) }
            if (unit != null) {
                Text(unit.uppercase(), style = unitStyle, color = tone.content.copy(alpha = 0.78f), modifier = Modifier.align(Alignment.Bottom).padding(bottom = if (big) 6.dp else 2.dp))
            }
        } else {
            Text(value.uppercase(), style = valueStyle, color = tone.content, modifier = Modifier.alignByBaseline())
            if (unit != null) {
                Text(unit.uppercase(), style = unitStyle, color = tone.content.copy(alpha = 0.78f), modifier = Modifier.alignByBaseline())
            }
        }
    }
}

/** Faixa de etiquetas inclinada −2°: dá movimento sem mexer no texto corrido. Pode sair da borda de um card. */
@Composable
fun TicketStrip(modifier: Modifier = Modifier, tilt: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.then(
            if (tilt) Modifier.graphicsLayer { rotationZ = -2f; transformOrigin = TransformOrigin(0f, 0.5f) } else Modifier,
        ),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Selo de alerta: triângulo laranja girado, sempre com texto ao lado. */
@Composable
fun WarningBadge(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Image(painterResource(R.drawable.badge_warning), contentDescription = null, modifier = modifier.size(size).rotate(-8f).wiggleOnce(Unit))
}

/** Segmentado em pílula (Esta semana | Plano base): a pílula Volt desliza até a opção tocada. */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val haptics = LocalHapticFeedback.current
    val position by animateFloatAsState(
        selected.toFloat(),
        if (motion == MotionLevel.Full) tween(Motion.Short, easing = Motion.EaseOut) else snap(),
        label = "pillTabs",
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(Palette.Surface)
            .padding(4.dp)
            // Indicador desenhado atrás das opções: a posição anima só na fase de desenho.
            .drawBehind {
                val gap = 4.dp.toPx()
                val width = (size.width - gap * (options.size - 1)) / options.size
                drawRoundRect(Palette.Volt, topLeft = Offset(position * (width + gap), 0f), size = Size(width, size.height), cornerRadius = CornerRadius(size.height / 2))
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            val color by animateColorAsState(if (on) Palette.Ink else Palette.TextMuted, tween(Motion.Short), label = "pillTabText")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable {
                        if (!on) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp), color = color)
            }
        }
    }
}

/** Chip de status: etiqueta pequena e chanfrada, a palavra sempre escrita. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, background: Color = Palette.SurfaceHigh, content: Color = Palette.Text) {
    Text(
        text.uppercase(),
        modifier = modifier
            .clip(TicketShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.4.sp),
        color = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextMuted) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

/** Marca de status: cada estado tem forma própria, não só cor (✓, ponto, contorno, ✕, traço). */
@Composable
fun StatusMark(state: DayState, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val ring = Modifier.size(size).border(2.dp, Palette.Neutral, CircleShape)
    when (state) {
        DayState.DONE -> Box(modifier.size(size).background(Palette.Volt, CircleShape), Alignment.Center) {
            AppIcon(R.drawable.ic_check, null, tint = Palette.Ink, size = size * 0.6f)
        }
        DayState.PARTIAL -> Box(modifier.size(size).border(2.dp, Palette.Volt, CircleShape).padding(4.dp).clip(CircleShape)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(Palette.Volt))
        }
        DayState.MOVED -> Box(modifier.size(size).border(2.dp, Palette.Flame, CircleShape), Alignment.Center) {
            AppIcon(R.drawable.ic_arrow_right, null, tint = Palette.Flame, size = size * 0.55f)
        }
        DayState.UNAVAILABLE -> Box(modifier.size(size).border(2.dp, Palette.Neutral, CircleShape), Alignment.Center) {
            Box(Modifier.width(size * 0.5f).height(2.dp).background(Palette.TextMuted, CircleShape))
        }
        DayState.MISSED, DayState.UNMARKED -> Box(modifier.size(size).border(2.dp, Palette.Flame, CircleShape), Alignment.Center) {
            AppIcon(R.drawable.ic_close, null, tint = Palette.Flame, size = size * 0.5f)
        }
        DayState.TODAY -> Box(modifier.size(size).border(2.dp, Palette.Volt, CircleShape), Alignment.Center) {
            Box(Modifier.size(size * 0.4f).background(Palette.Volt, CircleShape))
        }
        DayState.PLANNED -> Box(modifier.then(ring))
        DayState.REST -> Box(modifier.size(size), Alignment.Center) {
            Box(Modifier.width(size / 2).height(2.dp).background(Palette.Neutral, CircleShape))
        }
    }
}
