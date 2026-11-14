package com.myvitory.app.ui.weight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.data.formatKg
import com.myvitory.app.domain.GoalProgress
import com.myvitory.app.domain.Weighing
import com.myvitory.app.domain.WeightForecast
import com.myvitory.app.domain.WeightLogic
import com.myvitory.app.domain.WeightTrend
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.PrimaryButton
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.Palette
import java.time.LocalDate
import kotlin.math.abs

private val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

internal fun LocalDate.short() = "${dayOfMonth.twoDigits()}/${monthValue.twoDigits()}"

/** "Hoje", "Ontem", "12 out" ou "12 out 2025" (outro ano). */
internal fun LocalDate.longLabel(today: LocalDate) = when (this) {
    today -> "Hoje"
    today.minusDays(1) -> "Ontem"
    else -> "$dayOfMonth ${months[monthValue - 1]}" + if (year != today.year) " $year" else ""
}

internal fun Double.round1() = Math.round(this * 10) / 10.0

/** "+0,4" / "−1,2" / "0" */
private fun signedKg(kg: Double): String {
    val r = kg.round1()
    return when {
        r > 0 -> "+${formatKg(r)}"
        r < 0 -> "−${formatKg(abs(r))}"
        else -> "0"
    }
}

/** Diálogo aberto: [weighing] null = nova pesagem. */
private data class WeighingEdit(val weighing: Weighing?)

@Composable
fun WeightScreen(contentPadding: PaddingValues) {
    val vm = appViewModel { WeightViewModel(it.weights, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    var editing by remember { mutableStateOf<WeighingEdit?>(null) }
    var editingGoal by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.padding(start = 4.dp)) {
            Text(
                state.last?.let { "ÚLTIMA PESAGEM · ${it.date.longLabel(state.today).uppercase()}" } ?: "PESO",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted,
            )
            Text(
                state.last?.let { "${formatKg(it.kg)} kg" } ?: "Sem pesagens",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            monthChange(state.weights)?.let { (diff, since) ->
                Text("${signedKg(diff)} kg desde ${since.longLabel(state.today)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }

        if (state.due) {
            val days = state.daysSinceLast
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.FlameSoft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(R.drawable.ic_scale, null, tint = Palette.Flame, size = 20.dp)
                Text(
                    if (days == null) "Registre seu peso para ver a evolução e a previsão do mês. Uma pesagem por mês já basta."
                    else "Faz $days dias que você não se pesa. Pese pelo menos uma vez por mês.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        PrimaryButton("Registrar peso", onClick = { editing = WeighingEdit(null) }, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)

        if (state.weights.isNotEmpty()) {
            Card {
                WeightChart(state.weights, state.forecast, state.goal?.targetKg)
            }
            ForecastCard(state.trend, state.forecast, state.weights, state.today)
            GoalCard(state.progress, state.today, onEdit = { editingGoal = true })
            History(state.weights, state.today, onOpen = { editing = WeighingEdit(it) })
        }
    }

    editing?.let { (weighing) ->
        WeighingDialog(
            initial = weighing,
            today = state.today,
            suggestionKg = if (weighing == null) state.last?.kg else null,
            existing = state.weights.associate { it.date to it.kg },
            onDismiss = { editing = null },
            onSave = { date, kg ->
                editing = null
                // Mudou a data de uma pesagem: a antiga sai, a nova entra (o desfazer volta as duas).
                val moved = weighing?.takeIf { it.date != date }
                vm.record(date, kg) { revertRecord ->
                    if (moved == null) {
                        undo.show("Peso salvo: ${formatKg(kg)} kg", revertRecord)
                    } else {
                        vm.delete(moved) { revertDelete -> undo.show("Pesagem movida", { revertRecord(); revertDelete() }) }
                    }
                }
            },
            onDelete = weighing?.let { w ->
                {
                    editing = null
                    vm.delete(w) { revert -> undo.show("Pesagem apagada", revert) }
                }
            },
        )
    }

    if (editingGoal) {
        val current = state.last?.kg
        if (current != null) {
            GoalDialog(
                initial = state.goal,
                currentKg = current,
                today = state.today,
                onDismiss = { editingGoal = false },
                onSave = { kg, by -> editingGoal = false; vm.setGoal(kg, by) },
                onRemove = state.goal?.let { { editingGoal = false; vm.clearGoal() } },
            )
        }
    }
}

/** Variação em relação à pesagem de ~30 dias atrás (a mais próxima de um mês antes da última). */
private fun monthChange(weights: List<Weighing>): Pair<Double, LocalDate>? {
    val last = weights.lastOrNull() ?: return null
    val reference = weights.dropLast(1).lastOrNull { !it.date.isAfter(last.date.minusDays(25)) } ?: weights.firstOrNull()?.takeIf { it != last } ?: return null
    return (last.kg - reference.kg) to reference.date
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Palette.Surface, border = BorderStroke(1.dp, Palette.SurfaceHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun ForecastCard(trend: WeightTrend?, forecast: WeightForecast?, weights: List<Weighing>, today: LocalDate) {
    Card {
        SectionLabel("Próximo mês")
        if (forecast != null && trend != null) {
            Text("~${formatKg(forecast.kg.round1())} kg em ${forecast.date.longLabel(today)}", style = MaterialTheme.typography.headlineSmall)
            val pace = if (abs(trend.kgPerWeek) < 0.05) "Peso estável" else "${signedKg(trend.kgPerWeek)} kg por semana"
            Text(
                "$pace, pela tendência de ${trend.count} pesagens desde ${trend.from.longLabel(today)}. É uma estimativa: segue o ritmo atual.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        } else {
            val days = WeightLogic.daysSinceLast(weights, today) ?: 0
            Text(
                when {
                    days > WeightLogic.MAX_FORECAST_GAP_DAYS -> "A última pesagem tem mais de ${WeightLogic.MAX_FORECAST_GAP_DAYS} dias. Pese de novo para atualizar a previsão."
                    else -> "A previsão aparece com 2 pesagens a pelo menos ${WeightLogic.MIN_TREND_SPAN_DAYS} dias de distância."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        }
    }
}

@Composable
private fun GoalCard(progress: GoalProgress?, today: LocalDate, onEdit: () -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Meta", Modifier.weight(1f))
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (progress == null) "Definir" else "Editar", color = Palette.Volt, fontWeight = FontWeight.Bold)
            }
        }
        if (progress == null) {
            Text("Defina um peso alvo e, se quiser, um prazo. O app mostra quanto falta e quando você chega lá no ritmo atual.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            return@Card
        }
        val goal = progress.goal
        Text(
            if (progress.reached) "${formatKg(goal.targetKg)} kg · alcançada!" else "${formatKg(goal.targetKg)} kg · faltam ${formatKg(progress.remainingKg.round1())} kg",
            style = MaterialTheme.typography.headlineSmall,
            color = if (progress.reached) Palette.Volt else Palette.Text,
        )
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Palette.SurfaceHigh)) {
            Box(Modifier.fillMaxWidth(progress.fraction.toFloat()).height(8.dp).clip(CircleShape).background(Palette.Sky))
        }
        Row {
            Text("${formatKg(goal.startKg)} kg no início", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.weight(1f))
            Text("${(progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        if (!progress.reached) {
            val lines = buildList {
                progress.eta?.let { add("No ritmo atual, você chega lá por volta de ${it.longLabel(today)}.") }
                    ?: add("No ritmo atual ainda não dá para prever quando você chega lá.")
                val by = goal.by
                val needed = progress.neededKgPerWeek
                if (by != null && needed != null) {
                    val onTime = progress.eta?.let { !it.isAfter(by) } == true
                    add(
                        "Prazo ${by.longLabel(today)}: precisa de ${signedKg(needed)} kg por semana" +
                            if (onTime) " — no caminho certo." else "."
                    )
                } else if (by != null) {
                    add("O prazo (${by.longLabel(today)}) já passou.")
                }
            }
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted) }
        }
    }
}

@Composable
private fun History(weights: List<Weighing>, today: LocalDate, onOpen: (Weighing) -> Unit) {
    Column {
        SectionLabel("Histórico", Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp).semantics { heading() })
        val newestFirst = weights.asReversed()
        newestFirst.forEachIndexed { i, w ->
            val previous = newestFirst.getOrNull(i + 1)
            HorizontalDivider(color = Palette.Surface)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onOpen(w) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(w.date.longLabel(today), style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
                previous?.let {
                    Text("${signedKg(w.kg - it.kg)} kg", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(end = 14.dp))
                }
                Text("${formatKg(w.kg)} kg", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
