package com.myvitory.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvitory.app.data.formatKg
import com.myvitory.app.domain.Weighing
import com.myvitory.app.domain.WeightForecast
import com.myvitory.app.ui.theme.Barlow
import com.myvitory.app.ui.theme.Palette
import java.time.LocalDate
import kotlin.math.abs

/** O gráfico mostra no máximo um ano: pesagens mais antigas ficam só no histórico. */
private const val CHART_DAYS = 365L

/**
 * Peso no tempo (eixo X proporcional às datas, já que as pesagens têm intervalos irregulares).
 * Linha cheia lima: pesagens. Tracejado lima: previsão do próximo mês. Tracejado azul: meta.
 * Tocar perto de uma pesagem mostra o valor dela.
 */
@Composable
fun WeightChart(weights: List<Weighing>, forecast: WeightForecast?, goalKg: Double?, modifier: Modifier = Modifier) {
    val last = weights.last()
    val points = weights.filter { it.date >= last.date.minusDays(CHART_DAYS) }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontFamily = Barlow, fontSize = 11.sp, color = Palette.TextFaint)
    val labelStyle = TextStyle(fontFamily = Barlow, fontSize = 12.sp, color = Palette.Text)

    val start = points.first().date
    val end = forecast?.date?.takeIf { it > last.date } ?: last.date
    val span = (end.toEpochDay() - start.toEpochDay()).coerceAtLeast(1)
    val values = points.map { it.kg } + listOfNotNull(forecast?.kg, goalKg)
    val pad = ((values.max() - values.min()) * 0.1).coerceAtLeast(0.5)
    val lo = Math.floor(values.min() - pad)
    var hi = Math.ceil(values.max() + pad)
    if ((hi - lo).toInt() % 2 == 1) hi += 1 // meio da grade num número inteiro

    val point = points[selected]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${point.date.short()} · ${formatKg(point.kg)} kg", style = MaterialTheme.typography.bodySmall, color = Palette.Text)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics {
                    contentDescription = buildString {
                        append("Gráfico do peso: de ${formatKg(points.first().kg)} kg em ${points.first().date.short()} ")
                        append("para ${formatKg(last.kg)} kg em ${last.date.short()}.")
                        forecast?.let { append(" Previsão de ${formatKg(it.kg.round1())} kg em ${it.date.short()}.") }
                        goalKg?.let { append(" Meta de ${formatKg(it)} kg.") }
                    }
                }
                .pointerInput(points, span) {
                    detectTapGestures { tap ->
                        val left = 36.dp.toPx()
                        val right = size.width - 12.dp.toPx()
                        fun x(date: LocalDate) = left + (right - left) * (date.toEpochDay() - start.toEpochDay()) / span
                        selected = points.indices.minBy { abs(x(points[it].date) - tap.x) }
                    }
                },
        ) {
            val left = 36.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            fun x(date: LocalDate) = left + (right - left) * (date.toEpochDay() - start.toEpochDay()) / span
            fun y(kg: Double) = (bottom - (bottom - top) * ((kg - lo) / (hi - lo))).toFloat()
            val dashes = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

            listOf(lo, (lo + hi) / 2, hi).forEach { kg ->
                val gy = y(kg)
                drawLine(Palette.Line, Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx(), pathEffect = dashes)
                val label = measurer.measure(formatKg(kg), axisStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), gy - label.size.height / 2))
            }
            val first = measurer.measure(start.short(), axisStyle)
            drawText(first, topLeft = Offset(left, bottom + 6.dp.toPx()))
            if (end != start) {
                val endLabel = measurer.measure(end.short(), axisStyle)
                drawText(endLabel, topLeft = Offset(right - endLabel.size.width, bottom + 6.dp.toPx()))
            }

            goalKg?.let { goal ->
                val gy = y(goal)
                drawLine(Palette.Blue, Offset(left, gy), Offset(right, gy), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                val label = measurer.measure("Meta ${formatKg(goal)}", axisStyle.copy(color = Palette.Blue))
                val ly = if (gy - label.size.height - 2.dp.toPx() > top) gy - label.size.height - 2.dp.toPx() else gy + 2.dp.toPx()
                drawText(label, topLeft = Offset(left + 4.dp.toPx(), ly))
            }

            forecast?.takeIf { it.date > last.date }?.let { f ->
                drawLine(
                    Palette.Lime.copy(alpha = 0.7f), Offset(x(last.date), y(last.kg)), Offset(x(f.date), y(f.kg)),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
                drawCircle(Palette.Ground, radius = 5.dp.toPx(), center = Offset(x(f.date), y(f.kg)))
                drawCircle(Palette.Lime, radius = 3.dp.toPx(), center = Offset(x(f.date), y(f.kg)), style = Stroke(width = 1.5.dp.toPx()))
            }

            if (points.size >= 2) {
                val path = Path().apply {
                    points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.date), y(p.kg)) else lineTo(x(p.date), y(p.kg)) }
                }
                drawPath(path, Palette.Lime, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val sx = x(points[selected].date)
            val sy = y(points[selected].kg)
            drawLine(Palette.LineStrong, Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(Palette.Ground, radius = 6.dp.toPx(), center = Offset(sx, sy))
            drawCircle(Palette.Lime, radius = 4.dp.toPx(), center = Offset(sx, sy))

            val value = measurer.measure("${formatKg(last.kg)} kg", labelStyle)
            val vx = (x(last.date) - value.size.width / 2).coerceIn(left, right - value.size.width)
            val vy = (y(last.kg) - value.size.height - 8.dp.toPx()).coerceAtLeast(0f)
            drawText(value, topLeft = Offset(vx, vy))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("Pesagens", Palette.Lime, dashed = false)
            if (forecast != null) Legend("Previsão", Palette.Lime, dashed = true)
            if (goalKg != null) Legend("Meta", Palette.Blue, dashed = true)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { Box(Modifier.width(4.dp).height(2.dp).background(color, CircleShape)) }
            }
        } else {
            Box(Modifier.size(width = 18.dp, height = 2.dp).background(color, CircleShape))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}
