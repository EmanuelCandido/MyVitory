package com.myvitory.app.ui.components

import androidx.annotation.DrawableRes
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 58.dp),
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
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
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
) {
    val style = MaterialTheme.typography.labelLarge
    Row(
        modifier
            .clip(TicketShape)
            .background(tone.background)
            .padding(horizontal = if (big) 11.dp else 8.dp, vertical = if (big) 6.dp else 5.dp)
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(value.uppercase(), style = style.copy(fontSize = if (big) 30.sp else 14.sp, lineHeight = if (big) 32.sp else 17.sp, letterSpacing = 0.sp), color = tone.content, modifier = Modifier.alignByBaseline())
        if (unit != null) {
            Text(
                unit.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (big) 12.sp else 9.5.sp, letterSpacing = 0.4.sp),
                color = tone.content.copy(alpha = 0.78f),
                modifier = Modifier.alignByBaseline(),
            )
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
    Image(painterResource(R.drawable.badge_warning), contentDescription = null, modifier = modifier.size(size).rotate(-8f))
}

/** Segmentado em pílula (Esta semana | Plano base): a opção ativa em Volt. */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(Palette.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(if (on) Palette.Volt else Color.Transparent)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                    color = if (on) Palette.Ink else Palette.TextMuted,
                )
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
