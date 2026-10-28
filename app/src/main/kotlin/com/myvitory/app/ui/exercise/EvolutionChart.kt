package com.myvitory.app.ui.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvitory.app.data.db.ExerciseHistoryPoint
import com.myvitory.app.data.formatKg
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.Barlow
import com.myvitory.app.ui.theme.Palette
import java.time.LocalDate
import kotlin.math.abs

private fun LocalDate.short() = "${dayOfMonth.twoDigits()}/${monthValue.twoDigits()}"

/**
 * Maior carga por treino ao longo do tempo. Uma série só: sem legenda (o título nomeia),
 * linha de 2 dp, grade discreta, só o último ponto rotulado. Tocar num ponto mostra o detalhe.
 * A lista abaixo do gráfico é a versão em tabela, para quem prefere ler (e para o TalkBack).
 */
@Composable
fun EvolutionSection(points: List<ExerciseHistoryPoint>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sua evolução", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (points.isEmpty()) {
            Text("Ainda sem registros. Marque as séries no treino para ver sua evolução aqui.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            return@Column
        }
        val best = points.maxBy { it.topKg }
        val last = points.last()
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("Recorde", "${formatKg(best.topKg)} kg", best.date.short())
            Stat("Última", "${formatKg(last.topKg)} kg", last.date.short())
            if (points.size >= 2) {
                val diff = last.topKg - points.first().topKg
                Stat("Desde ${points.first().date.short()}", (if (diff >= 0) "+" else "−") + "${formatKg(abs(diff))} kg", "${points.size} treinos")
            }
        }
        if (points.size >= 2) {
            LineChart(points)
        }
        Column {
            Text("Maior carga por treino (kg)", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(bottom = 4.dp))
            points.takeLast(5).asReversed().forEach { p ->
                HorizontalDivider(color = Palette.Surface)
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp)) {
                    Text(p.date.short(), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
                    Text("${formatKg(p.topKg)} kg × ${p.reps}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, detail: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
    }
}

@Composable
private fun LineChart(points: List<ExerciseHistoryPoint>) {
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = Barlow, fontSize = 11.sp, color = Palette.TextFaint)
    val labelStyle = TextStyle(fontFamily = Barlow, fontSize = 12.sp, color = Palette.Text)

    val minKg = points.minOf { it.topKg }
    val maxKg = points.maxOf { it.topKg }
    // Folga para a linha não colar nas bordas, com a grade em múltiplos de 5 kg (115 / 130 / 145).
    val pad = ((maxKg - minKg) * 0.1).coerceAtLeast(2.5)
    val lo = (Math.floor((minKg - pad) / 5) * 5).coerceAtLeast(0.0)
    var hi = Math.ceil((maxKg + pad) / 5) * 5
    if (((hi - lo) / 5).toInt() % 2 == 1) hi += 5 // meio da grade também cai num múltiplo de 5

    val point = points[selected]
    Text(
        "${point.date.short()} · ${formatKg(point.topKg)} kg × ${point.reps} · volume ${formatKg(point.volumeKg)} kg",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Text,
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics {
                contentDescription = "Gráfico da maior carga por treino: de ${formatKg(points.first().topKg)} kg em ${points.first().date.short()} " +
                    "para ${formatKg(points.last().topKg)} kg em ${points.last().date.short()}. Recorde ${formatKg(maxKg)} kg."
            }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val left = 36.dp.toPx()
                    val step = (size.width - left - 12.dp.toPx()) / (points.size - 1)
                    selected = ((tap.x - left) / step).toInt().coerceIn(0, points.lastIndex).let { i ->
                        if (i < points.lastIndex && (tap.x - left) - i * step > step / 2) i + 1 else i
                    }
                }
            },
    ) {
        val left = 36.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun x(i: Int) = left + (right - left) * i / (points.size - 1)
        fun y(kg: Double) = (bottom - (bottom - top) * ((kg - lo) / (hi - lo))).toFloat()

        // Grade: três linhas finas e tracejadas, rótulos em tinta discreta.
        listOf(lo, (lo + hi) / 2, hi).forEach { kg ->
            val gy = y(kg)
            drawLine(Palette.Line, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val label = measurer.measure(formatKg(kg.roundTo(0.5)), axisStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), gy - label.size.height / 2))
        }
        // Datas só nas pontas.
        val first = measurer.measure(points.first().date.short(), axisStyle)
        drawText(first, topLeft = Offset(left, bottom + 6.dp.toPx()))
        val lastLabel = measurer.measure(points.last().date.short(), axisStyle)
        drawText(lastLabel, topLeft = Offset(right - lastLabel.size.width, bottom + 6.dp.toPx()))

        val path = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.topKg)) else lineTo(x(i), y(p.topKg)) }
        }
        drawPath(path, Palette.Lime, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Ponto selecionado: marcador de 8 dp com anel da cor do fundo; o último ponto leva o valor escrito.
        val sx = x(selected)
        val sy = y(points[selected].topKg)
        drawLine(Palette.LineStrong, Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
        drawCircle(Palette.Ground, radius = 6.dp.toPx(), center = Offset(sx, sy))
        drawCircle(Palette.Lime, radius = 4.dp.toPx(), center = Offset(sx, sy))
        if (selected != points.lastIndex) {
            drawCircle(Palette.Ground, radius = 5.dp.toPx(), center = Offset(x(points.lastIndex), y(points.last().topKg)))
            drawCircle(Palette.Lime, radius = 3.dp.toPx(), center = Offset(x(points.lastIndex), y(points.last().topKg)))
        }
        val value = measurer.measure("${formatKg(points.last().topKg)} kg", labelStyle)
        val vx = (x(points.lastIndex) - value.size.width).coerceAtLeast(left)
        val vy = (y(points.last().topKg) - value.size.height - 8.dp.toPx()).coerceAtLeast(0f)
        drawText(value, topLeft = Offset(vx, vy))
    }
}

private fun Double.roundTo(step: Double) = Math.round(this / step) * step
