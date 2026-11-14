package com.myvitory.app.ui.today

import androidx.annotation.DrawableRes
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.IconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvitory.app.data.db.TemplateItemRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.data.DayView
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.DecisionPlate
import com.myvitory.app.ui.components.PaperButton
import com.myvitory.app.ui.components.Ticket
import com.myvitory.app.ui.components.TicketStrip
import com.myvitory.app.ui.components.TicketTone
import com.myvitory.app.ui.components.WarningBadge
import com.myvitory.app.ui.components.MarkSessionSheet
import com.myvitory.app.ui.components.PrimaryButton
import com.myvitory.app.ui.components.SecondaryButton
import com.myvitory.app.ui.components.SessionExerciseList
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.StatusMark
import com.myvitory.app.ui.components.headerLabel
import com.myvitory.app.ui.components.label
import com.myvitory.app.ui.components.letter
import com.myvitory.app.ui.components.long
import com.myvitory.app.ui.components.short
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.Palette
import com.myvitory.app.ui.update.UpdateCard
import java.time.LocalDate

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenWorkout: (Long) -> Unit,
    onOpenReplan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onOpenWeight: () -> Unit,
) {
    val vm = appViewModel { TodayViewModel(it.training, it.coach, it.weights) }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    var marking by remember { mutableStateOf<DayView?>(null) }

    fun mark(session: SessionEntity, status: SessionStatus, message: String) =
        vm.mark(session, status) { revert -> undo.show(message, revert) }

    /** Treino perdido: marca e já abre a proposta de nova semana. */
    fun markMissed(session: SessionEntity) =
        vm.mark(session, SessionStatus.MISSED) { revert ->
            undo.show("Marcado como não treinado", revert)
            onOpenReplan()
        }

    // Pergunta do dia seguinte: abre sozinha uma vez por treino sem registro.
    val unmarkedId = state.unmarked?.session?.id
    LaunchedEffect(unmarkedId) {
        if (unmarkedId != null && vm.shouldAsk(unmarkedId)) marking = state.unmarked
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            // Data e progresso como rótulo e etiqueta: o nome do treino, logo abaixo, é o único título grande da tela.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.today.headerLabel(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                if (state.plannedCount == 0) {
                    Ticket("Semana livre", tone = TicketTone.Dim)
                } else {
                    Ticket(
                        "${state.doneCount}/${state.plannedCount}",
                        unit = "treinos na semana",
                        tone = if (state.doneCount == state.plannedCount) TicketTone.Volt else TicketTone.Dim,
                        description = "${state.doneCount} de ${state.plannedCount} treinos na semana",
                    )
                }
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(44.dp).border(2.dp, Palette.Line, CircleShape), Alignment.Center) {
                    AppIcon(R.drawable.ic_settings, "Ajustes e lembretes", size = 20.dp)
                }
            }
        }

        WeekStrip(state.week, state.today, onOpenWeek, Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp))

        UpdateCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))

        state.unmarked?.let { day ->
            Banner(
                text = "${day.date.dayOfWeek.long()} (${day.session?.name}) ficou sem registro.",
                action = "Marcar",
                onAction = { marking = day },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }
        if (state.unmarked == null && state.pendingMissed.isNotEmpty()) {
            val names = state.pendingMissed.joinToString(" e ") { "${it.name} (${it.date.dayOfWeek.short().lowercase()})" }
            Banner(
                text = "$names ficou de fora.",
                action = "Reorganizar",
                onAction = onOpenReplan,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }

        // Pesagem do mês: só aparece sem aviso de treino na frente, que pede decisão antes.
        if (!state.loading && state.weightDue && state.unmarked == null && state.pendingMissed.isEmpty()) {
            Banner(
                text = state.daysSinceWeight?.let { "Faz $it dias desde a última pesagem." } ?: "Registre seu peso para acompanhar a evolução.",
                action = "Pesar",
                onAction = onOpenWeight,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                icon = R.drawable.ic_scale,
            )
        }

        val todayDay = state.todayDay
        val session = todayDay?.session
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            when {
                state.loading -> Spacer(Modifier.height(380.dp))
                session == null -> RestCard(state.next)
                session.status == SessionStatus.PLANNED -> TodayCard(
                    day = todayDay,
                    state = state,
                    onStart = { onOpenWorkout(session.id) },
                    onAlreadyDone = { marking = todayDay },
                    onSkip = { markMissed(session) },
                    onOpenExercise = onOpenExercise,
                )
                session.status == SessionStatus.MISSED -> MissedCard(
                    session = session,
                    canReplan = state.pendingMissed.any { it.id == session.id },
                    onReplan = onOpenReplan,
                    onTrainAnyway = { mark(session, SessionStatus.PLANNED, "Treino de hoje reaberto") },
                )
                else -> DoneCard(session, state.next, onOpen = { onOpenWorkout(session.id) })
            }
        }
    }

    marking?.let { day ->
        val session = day.session ?: return@let
        val isToday = day.date == state.today
        MarkSessionSheet(
            eyebrow = if (isToday) "Hoje · ${session.name}" else "${day.date.dayOfWeek.long()} · sem registro",
            title = if (isToday) "Como foi ${session.name}?" else "Você treinou ${session.name}?",
            onAllDone = { marking = null; mark(session, SessionStatus.DONE, "Treino marcado como feito") },
            onPartial = { marking = null; onOpenWorkout(session.id) },
            onMissed = { marking = null; markMissed(session) },
            onDismiss = { marking = null },
        )
    }
}

@Composable
private fun WeekStrip(days: List<DayView>, today: LocalDate, onOpenWeek: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        days.forEach { day ->
            val isToday = day.date == today
            val description = buildString {
                append(day.date.dayOfWeek.long())
                day.session?.let { append(", ${it.name}") } ?: append(", descanso")
                day.state.label()?.let { append(", $it") }
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (isToday) Modifier.background(Palette.Surface).border(2.dp, Palette.Volt, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(onClick = onOpenWeek)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(day.date.dayOfWeek.letter(), style = MaterialTheme.typography.labelMedium, color = if (isToday) Palette.Volt else Palette.TextMuted)
                Text(
                    day.date.dayOfMonth.twoDigits(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    color = if (day.session == null) Palette.TextMuted else Palette.Text,
                )
                StatusMark(day.state)
            }
        }
    }
}

/** Pendência: selo de alerta (ou ícone), frase curta e o verbo numa pílula Flame. Só uma acima do treino por vez. */
@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.FlameSoft)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon == null) WarningBadge(size = 26.dp) else AppIcon(icon, null, tint = Palette.Flame, size = 22.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Surface(onClick = onAction, shape = CircleShape, color = Palette.Flame, contentColor = Palette.Ink, modifier = Modifier.heightIn(min = 44.dp)) {
            Box(Modifier.padding(horizontal = 14.dp), Alignment.Center) {
                Text(action.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp))
            }
        }
    }
}

@Composable
private fun CardShell(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Palette.Surface,
        contentColor = Palette.Text,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
private fun TodayCard(
    day: DayView,
    state: TodayUiState,
    onStart: () -> Unit,
    onAlreadyDone: () -> Unit,
    onSkip: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val session = day.session ?: return
    val started = session.startedAt != null
    val sets = state.preview.sumOf { it.sets }
    val minutes = estimateMinutes(state.preview)
    Box {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Palette.Surface,
            contentColor = Palette.Text,
            border = BorderStroke(2.dp, Palette.Volt),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 30.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val movedFrom = state.movedFrom
                    if (movedFrom != null) {
                        SectionLabel("Veio de ${movedFrom.dayOfWeek.long()}", color = Palette.Flame)
                    } else {
                        SectionLabel("Treino de hoje")
                    }
                    Text(session.name.uppercase(), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                    if (session.mergedTemplateId != null) {
                        Text("Dois treinos em um, uma série a menos em cada.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                }
                if (state.preview.isNotEmpty()) {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        HorizontalDivider(color = Palette.Line)
                        SessionExerciseList(state.preview, onOpenExercise)
                        Text("Toque em um exercício para ver o movimento e o vídeo.", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                // A pergunta do dia numa placa só: as alternativas em branco, a recomendada em Volt, por último.
                DecisionPlate {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperButton("Já treinei", onAlreadyDone, Modifier.weight(1f))
                        PaperButton("Hoje não vai dar", onSkip, Modifier.weight(1f))
                    }
                    PrimaryButton(if (started) "Continuar treino" else "Começar treino", onStart, Modifier.fillMaxWidth(), icon = R.drawable.ic_play)
                }
            }
        }
        // Número antes do rótulo, como o contador da referência: sai da borda de cima do card.
        TicketStrip(Modifier.padding(start = 18.dp)) {
            Ticket("${day.exerciseCount}", unit = "exercícios", description = "${day.exerciseCount} exercícios")
            if (sets > 0) Ticket("$sets", unit = "séries", description = "$sets séries")
            if (minutes > 0) Ticket("~$minutes", unit = "min", description = "cerca de $minutes minutos")
        }
    }
}

/** Mesma conta do painel (web/src/lib/analysis.ts): 3 s por repetição mais o descanso entre as séries. */
private fun estimateMinutes(items: List<TemplateItemRow>): Int {
    val seconds = items.sumOf { it.sets * it.reps * 3 + (it.sets - 1).coerceAtLeast(0) * it.restSec }
    return Math.round(seconds / 60.0).toInt()
}

@Composable
private fun DoneCard(session: SessionEntity, next: DayView?, onOpen: () -> Unit) {
    CardShell {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusMark(if (session.status == SessionStatus.DONE) DayState.DONE else DayState.PARTIAL, size = 44.dp)
            Column {
                SectionLabel(if (session.status == SessionStatus.DONE) "Treino concluído" else "Treino parcial")
                Text(session.name.uppercase(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
        }
        NextLine(next)
        SecondaryButton("Ver treino", onOpen, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MissedCard(session: SessionEntity, canReplan: Boolean, onReplan: () -> Unit, onTrainAnyway: () -> Unit) {
    CardShell {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel("Hoje sem treino")
            Text(session.name.uppercase(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                when {
                    canReplan -> "Marcado como não feito. Dá para encaixar no resto da semana."
                    session.movedTo != null -> "Foi para ${session.movedTo.dayOfWeek.long().lowercase()}."
                    else -> "Marcado como não feito."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
        }
        if (canReplan) {
            DecisionPlate {
                PaperButton("Vou treinar sim", onTrainAnyway, Modifier.fillMaxWidth())
                PrimaryButton("Reorganizar semana", onReplan, Modifier.fillMaxWidth())
            }
        } else {
            SecondaryButton("Vou treinar sim", onTrainAnyway, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RestCard(next: DayView?) {
    CardShell {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.SurfaceHigh), Alignment.Center) {
                Box(Modifier.size(width = 18.dp, height = 3.dp).background(Palette.Sky, CircleShape))
            }
            Column {
                SectionLabel("Hoje")
                Text("DIA DE DESCANSO", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
        }
        Text("O músculo cresce na recuperação. Aproveite.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        NextLine(next)
    }
}

@Composable
private fun NextLine(next: DayView?) {
    val session = next?.session ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Próximo · ${next.date.dayOfWeek.long()}  ", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        Text(session.name, style = MaterialTheme.typography.bodyMedium)
    }
}
