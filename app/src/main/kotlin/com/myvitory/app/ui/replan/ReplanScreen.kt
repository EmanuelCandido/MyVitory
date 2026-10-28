package com.myvitory.app.ui.replan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.domain.planner.Planner
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.PillTabs
import com.myvitory.app.ui.components.PrimaryButton
import com.myvitory.app.ui.components.SecondaryButton
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.Tag
import com.myvitory.app.ui.components.long
import com.myvitory.app.ui.components.short
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.Palette

@Composable
fun ReplanScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReplanViewModel(it.coach) }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar") }
            Text("Nova semana", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }

        val data = state.data
        when {
            state.loading -> Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Palette.Lime)
            }
            data == null -> Text(
                "Nenhum treino perdido para reorganizar nesta semana.",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextMuted,
                modifier = Modifier.padding(20.dp),
            )
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val missedNames = data.missed.joinToString(" e ") { "${it.name} (${it.date.dayOfWeek.short().lowercase()})" }
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Palette.Text, fontWeight = FontWeight.Bold)) {
                                append(if (data.missed.size == 1) "$missedNames ficou de fora. " else "$missedNames ficaram de fora. ")
                            }
                            append("Reorganizamos o resto da semana respeitando o descanso de cada músculo.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextMuted,
                    )
                    if (state.hasAlternative) {
                        PillTabs(listOf("Recomendada", "Alternativa"), state.tab, vm::selectTab)
                    }
                    state.explanation?.let { Changes(it, state.onlySkip) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Palette.Ground)
                        .border(BorderStroke(1.dp, Palette.Surface))
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val skip = {
                        vm.skip { revert ->
                            undo.show("Treino perdido fica de fora", revert)
                            onBack()
                        }
                    }
                    if (state.onlySkip) {
                        PrimaryButton("Pular treino perdido", skip, Modifier.fillMaxWidth(), enabled = !state.busy)
                    } else {
                        SecondaryButton("Pular", skip, Modifier.weight(1f).heightIn(min = 58.dp))
                        PrimaryButton(
                            "Aplicar",
                            {
                                vm.apply { revert ->
                                    undo.show("Semana reorganizada", revert)
                                    onBack()
                                }
                            },
                            Modifier.weight(1.7f),
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Changes(explanation: Planner.Explanation, onlySkip: Boolean) {
    if (onlySkip) {
        Text(
            "Não dá para encaixar sem apertar o descanso dos músculos. O melhor é pular e seguir o plano.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    explanation.changes.forEach { change -> ChangeCard(change) }
    if (explanation.changes.isEmpty() && !onlySkip) {
        Text("Nenhum dia muda.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
    }
    val droppedMissed = explanation.dropped
    if (droppedMissed.isNotEmpty()) {
        Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Fica de fora nesta semana")
                droppedMissed.forEach { w ->
                    Text(w.name, style = MaterialTheme.typography.titleMedium, color = Palette.Orange)
                }
                Text(
                    "Não cabe sem treinar músculo cansado. O próximo treino já considera isso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        }
    }
    explanation.nextRest?.let { next ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            AppIcon(R.drawable.ic_timer, null, tint = if (next.ok) Palette.Blue else Palette.Orange, size = 18.dp)
            val day = next.training.date.dayOfWeek.short().lowercase()
            val text = when {
                next.muscle == null -> "Próxima semana começa com ${next.training.name} ($day), sem conflito."
                next.ok -> "${next.muscle.label} na $day (${next.training.name}) terá ${next.gapHours} h de descanso."
                else -> "Atenção: ${next.muscle.label.lowercase()} terá só ${next.gapHours} h antes de ${next.training.name} ($day)."
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (next.ok) Palette.Blue else Palette.Orange, fontWeight = FontWeight.Bold)) {
                        append(if (next.ok) "Próximo descanso ok: " else "")
                    }
                    append(text)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
        }
    }
}

@Composable
private fun ChangeCard(change: Planner.DayChange) {
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(44.dp)) {
                Text(change.date.dayOfWeek.short(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                Text(change.date.dayOfMonth.twoDigits(), style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    change.before?.name ?: "Descanso",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                    textDecoration = TextDecoration.LineThrough,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        change.after?.name ?: "Descanso",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.1f),
                        color = if (change.after == null) Palette.TextMuted else Palette.Text,
                    )
                    if (change.after?.missed == true) Tag("VEIO DE ${change.after.originalDate.dayOfWeek.short()}", background = Palette.Lime, content = Palette.Ground)
                    if (change.usesRestDay) Tag("USA FOLGA", background = Palette.OrangeSoft, content = Palette.Orange)
                }
                Text(
                    reasonText(change),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 18.dp),
                )
            }
        }
    }
}

private fun reasonText(change: Planner.DayChange): String {
    val after = change.after ?: return "Treino saiu deste dia: vira descanso."
    val reason = change.reason
    val base = when {
        reason?.muscle == null -> "Músculos principais sem treino recente."
        reason.daysSince == 1 -> "Último treino de ${reason.muscle.label.lowercase()}: ontem."
        else -> "Último treino de ${reason.muscle.label.lowercase()}: há ${reason.daysSince} dias."
    }
    val moved = if (!after.missed && after.originalDate != change.date) " Era de ${after.originalDate.dayOfWeek.long().lowercase()}." else ""
    return base + moved
}
