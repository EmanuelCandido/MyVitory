package com.myvitory.app.ui.today

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.data.DayView
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.MarkSessionSheet
import com.myvitory.app.ui.components.PrimaryButton
import com.myvitory.app.ui.components.SecondaryButton
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.StatusMark
import com.myvitory.app.ui.components.headerLabel
import com.myvitory.app.ui.components.label
import com.myvitory.app.ui.components.letter
import com.myvitory.app.ui.components.long
import com.myvitory.app.ui.components.short
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.Palette
import java.time.LocalDate

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenWorkout: (Long) -> Unit,
    onOpenReplan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeek: () -> Unit,
) {
    val vm = appViewModel { TodayViewModel(it.training, it.coach) }
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
            Column(Modifier.weight(1f)) {
                Text(state.today.headerLabel(), style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
                Text(
                    if (state.plannedCount == 0) "Semana livre" else "${state.doneCount} de ${state.plannedCount} treinos na semana",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Box(Modifier.size(44.dp).border(1.dp, Palette.Line, CircleShape), Alignment.Center) {
                    AppIcon(R.drawable.ic_settings, "Ajustes e lembretes", size = 20.dp)
                }
            }
        }

        WeekStrip(state.week, state.today, onOpenWeek, Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp))

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
                    .then(if (isToday) Modifier.background(Palette.Surface).border(1.5.dp, Palette.Lime, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(onClick = onOpenWeek)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .padding(top = 8.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(day.date.dayOfWeek.letter(), style = MaterialTheme.typography.labelMedium, color = if (isToday) Palette.Lime else Palette.TextMuted)
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

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.OrangeSoft)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(R.drawable.ic_timer, null, tint = Palette.Orange, size = 20.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(action, color = Palette.Orange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CardShell(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.SurfaceHigh),
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
) {
    val session = day.session ?: return
    val started = session.startedAt != null
    CardShell {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val movedFrom = state.movedFrom
            if (movedFrom != null) {
                Text("VEIO DE ${movedFrom.dayOfWeek.long().uppercase()}", style = MaterialTheme.typography.labelSmall, color = Palette.Orange)
            } else {
                SectionLabel("Treino de hoje")
            }
            Text(session.name, style = MaterialTheme.typography.displaySmall)
            Text("${day.exerciseCount} exercícios", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
        if (state.preview.isNotEmpty()) {
            Column {
                val shown = state.preview.take(3)
                shown.forEach { item ->
                    HorizontalDivider(color = Palette.Line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.exerciseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${item.sets} × ${item.reps}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                    }
                }
                val rest = state.preview.size - shown.size
                if (rest > 0) {
                    HorizontalDivider(color = Palette.Line)
                    Text("+ $rest exercícios", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
        PrimaryButton(if (started) "Continuar treino" else "Começar treino", onStart, Modifier.fillMaxWidth(), icon = R.drawable.ic_play)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Já treinei", onAlreadyDone, Modifier.weight(1f))
            SecondaryButton("Hoje não vai dar", onSkip, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DoneCard(session: SessionEntity, next: DayView?, onOpen: () -> Unit) {
    CardShell {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusMark(if (session.status == SessionStatus.DONE) DayState.DONE else DayState.PARTIAL, size = 44.dp)
            Column {
                SectionLabel(if (session.status == SessionStatus.DONE) "Treino concluído" else "Treino parcial")
                Text(session.name, style = MaterialTheme.typography.headlineMedium)
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
            Text(session.name, style = MaterialTheme.typography.headlineMedium)
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
        if (canReplan) PrimaryButton("Reorganizar semana", onReplan, Modifier.fillMaxWidth())
        SecondaryButton("Vou treinar sim", onTrainAnyway, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RestCard(next: DayView?) {
    CardShell {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.SurfaceHigh), Alignment.Center) {
                Box(Modifier.size(width = 18.dp, height = 3.dp).background(Palette.Blue, CircleShape))
            }
            Column {
                SectionLabel("Hoje")
                Text("Dia de descanso", style = MaterialTheme.typography.headlineMedium)
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
