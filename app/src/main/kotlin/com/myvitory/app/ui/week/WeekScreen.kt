package com.myvitory.app.ui.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.data.DayView
import com.myvitory.app.data.PlanRow
import com.myvitory.app.data.TemplateSummary
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.PillTabs
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.SessionExerciseList
import com.myvitory.app.ui.components.StatusMark
import com.myvitory.app.ui.components.Tag
import com.myvitory.app.ui.components.label
import com.myvitory.app.ui.components.long
import com.myvitory.app.ui.components.short
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.components.weekRangeLabel
import com.myvitory.app.ui.theme.Palette
import kotlinx.coroutines.flow.flowOf

@Composable
fun WeekScreen(
    contentPadding: PaddingValues,
    onOpenWorkout: (Long) -> Unit,
    onOpenTemplate: (Long) -> Unit,
    onOpenReplan: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val vm = appViewModel { WeekViewModel(it.training) }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var daySheet by remember { mutableStateOf<DayView?>(null) }
    var planSheet by remember { mutableStateOf<PlanRow?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Semana", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    if (tab == 0) weekRangeLabel(state.weekStart) else "Repete toda semana",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
            }
            if (tab == 0) {
                RoundIconButton(R.drawable.ic_arrow_back, "Semana anterior") { vm.shiftWeek(-1) }
                RoundIconButton(R.drawable.ic_arrow_right, "Próxima semana") { vm.shiftWeek(1) }
            }
        }

        PillTabs(listOf("Esta semana", "Plano base"), tab, { tab = it }, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))

        if (tab == 0) {
            val planned = state.days.count { it.session != null }
            val done = state.days.count { it.state == DayState.DONE || it.state == DayState.PARTIAL }
            Text(
                if (planned == 0) "Nenhum treino nesta semana" else "$done de $planned treinos feitos",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
            )
            Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.days.forEach { day ->
                    // Dias passados só abrem com treino; de hoje em diante qualquer dia abre (para avisar indisponibilidade).
                    val canOpen = day.session != null || !day.date.isBefore(state.today)
                    DayRow(day, isToday = day.date == state.today, enabled = canOpen, onClick = { daySheet = day })
                }
            }
        } else {
            PlanBase(
                plan = state.plan,
                templates = state.templates,
                onDay = { planSheet = it },
                onTemplate = onOpenTemplate,
                onNewTemplate = { vm.createTemplate(onOpenTemplate) },
            )
        }
    }

    daySheet?.let { day ->
        val session = day.session
        val items by remember(session?.id, session?.templateId, session?.mergedTemplateId) {
            session?.let { vm.itemsFor(it) } ?: flowOf(emptyList())
        }.collectAsStateWithLifecycle(emptyList())
        val notChanged: (Boolean) -> Unit = { ok -> if (!ok) undo.show("Esse dia já foi começado e não pode mudar", null) }
        DayActionsSheet(
            day = day,
            items = items,
            templates = state.templates,
            canChangeThisWeek = day.closedLabel == null && !day.date.isBefore(state.today) &&
                (session == null || (session.status == SessionStatus.PLANNED && session.startedAt == null)),
            onOpenExercise = { daySheet = null; onOpenExercise(it) },
            onPickThisWeek = { templateId ->
                daySheet = null
                vm.setDayThisWeek(day.date, templateId) { ok ->
                    notChanged(ok)
                    if (ok) undo.show(if (templateId == null) "Descanso só nesta semana" else "Treino trocado só nesta semana", null)
                }
            },
            onCustomize = {
                daySheet = null
                session?.let { vm.customizeDay(it) { id -> if (id != null) onOpenTemplate(id) else notChanged(false) } }
            },
            onReset = {
                daySheet = null
                vm.resetDay(day.date) { ok -> notChanged(ok); if (ok) undo.show("Dia voltou ao plano base", null) }
            },
            isPastOrToday = !day.date.isAfter(state.today),
            canChangeAvailability = !day.date.isBefore(state.today),
            onOpen = { daySheet = null; session?.let { onOpenWorkout(it.id) } },
            onMark = { status, message ->
                daySheet = null
                session?.let {
                    vm.mark(it, status) { revert ->
                        undo.show(message, revert)
                        if (status == SessionStatus.MISSED && WeekLogic.replanCovers(day.date, state.today)) onOpenReplan()
                    }
                }
            },
            onAvailability = { unavailable ->
                daySheet = null
                vm.setUnavailable(day.date, unavailable) { revert ->
                    undo.show(if (unavailable) "Dia marcado como indisponível" else "Dia disponível de novo", revert)
                    // Havia treino nesse dia: já propõe onde encaixar.
                    if (unavailable && session?.status == SessionStatus.PLANNED && WeekLogic.replanCovers(day.date, state.today)) onOpenReplan()
                }
            },
            onDismiss = { daySheet = null },
        )
    }

    planSheet?.let { row ->
        PlanDaySheet(
            row = row,
            templates = state.templates,
            onPick = { id -> planSheet = null; vm.setPlanDay(row.day, id) },
            onDismiss = { planSheet = null },
        )
    }
}

@Composable
private fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(Modifier.size(44.dp).border(1.dp, Palette.Line, CircleShape), Alignment.Center) {
            AppIcon(icon, description, size = 18.dp)
        }
    }
}

@Composable
private fun DayLabel(top: String, bottom: String, modifier: Modifier = Modifier) {
    Column(modifier.width(44.dp)) {
        Text(top, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 0.9f), color = Palette.TextMuted)
        Text(bottom, style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 0.85f))
    }
}

@Composable
private fun DayRow(day: DayView, isToday: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val session = day.session
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (isToday) Modifier.background(Palette.Surface).border(1.5.dp, Palette.Line, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            // "SEG 26" vira "Segunda, dia 26" no TalkBack, com o treino e o estado numa frase só.
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    "${day.date.dayOfWeek.long()}, dia ${day.date.dayOfMonth}",
                    session?.name ?: "descanso",
                    session?.movedTo?.let { "foi para ${it.dayOfWeek.long().lowercase()}" },
                    "só nesta semana".takeIf { day.special },
                    day.state.label()?.lowercase(),
                ).joinToString(", ")
            }
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DayLabel(day.date.dayOfWeek.short(), day.date.dayOfMonth.twoDigits())
        Column(Modifier.weight(1f)) {
            val missed = day.state == DayState.MISSED || day.state == DayState.MOVED
            Text(
                session?.name ?: "Descanso",
                style = MaterialTheme.typography.titleMedium,
                color = if (session == null || missed) Palette.TextFaint else Palette.Text,
                textDecoration = if (missed) TextDecoration.LineThrough else null,
            )
            if (session != null) {
                val movedTo = session.movedTo
                Text(
                    when {
                        movedTo != null -> "Foi para ${movedTo.dayOfWeek.long().lowercase()}"
                        day.special -> "${day.exerciseCount} exercícios · só nesta semana"
                        else -> "${day.exerciseCount} exercícios"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (day.special && movedTo == null) Palette.Lime else Palette.TextMuted,
                )
            } else if (day.special) {
                Text("Só nesta semana", style = MaterialTheme.typography.bodySmall, color = Palette.Lime)
            }
        }
        day.state.label()?.let { text ->
            when (day.state) {
                DayState.DONE -> Tag(text, background = Palette.Lime, content = Palette.Ground)
                DayState.PARTIAL -> Tag(text, background = Palette.SurfaceHigh, content = Palette.Lime)
                DayState.UNAVAILABLE -> Tag(day.closedLabel ?: text, background = Palette.SurfaceHigh, content = Palette.TextMuted)
                DayState.MISSED, DayState.UNMARKED, DayState.MOVED -> Tag(text, background = Palette.OrangeSoft, content = Palette.Orange)
                DayState.TODAY -> Tag(text, background = Palette.Ground, content = Palette.Lime, modifier = Modifier.border(1.5.dp, Palette.Lime, RoundedCornerShape(8.dp)))
                else -> Unit
            }
        } ?: StatusMark(day.state)
    }
}

@Composable
private fun PlanBase(
    plan: List<PlanRow>,
    templates: List<TemplateSummary>,
    onDay: (PlanRow) -> Unit,
    onTemplate: (Long) -> Unit,
    onNewTemplate: () -> Unit,
) {
    Text(
        "Toque em um dia para escolher o treino.",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextMuted,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
    Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        plan.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onDay(row) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DayLabel(row.day.short(), row.template?.code ?: "—")
                Column(Modifier.weight(1f)) {
                    Text(row.template?.name ?: "Descanso", style = MaterialTheme.typography.titleMedium, color = if (row.template == null) Palette.TextFaint else Palette.Text)
                    if (row.template != null) Text("${row.exerciseCount} exercícios", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                AppIcon(R.drawable.ic_chevron_right, null, tint = Palette.Neutral, size = 18.dp)
            }
        }
    }

    SectionLabel("Seus treinos", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        templates.forEach { summary ->
            Surface(
                onClick = { onTemplate(summary.template.id) },
                shape = RoundedCornerShape(16.dp),
                color = Palette.Surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.SurfaceHigh), Alignment.Center) {
                        Text(summary.template.code, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(summary.template.name, style = MaterialTheme.typography.titleMedium)
                        Text("${summary.exerciseCount} exercícios", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                    }
                    AppIcon(R.drawable.ic_edit, "Editar ${summary.template.name}", tint = Palette.TextMuted, size = 18.dp)
                }
            }
        }
        Surface(
            onClick = onNewTemplate,
            shape = RoundedCornerShape(16.dp),
            color = Palette.Ground,
            border = BorderStroke(1.dp, Palette.LineStrong),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_plus, null, tint = Palette.Lime, size = 18.dp)
                Text("  Novo treino", style = MaterialTheme.typography.titleMedium, color = Palette.Lime)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayActionsSheet(
    day: DayView,
    items: List<TemplateItemRow>,
    templates: List<TemplateSummary>,
    canChangeThisWeek: Boolean,
    onOpenExercise: (String) -> Unit,
    onPickThisWeek: (Long?) -> Unit,
    onCustomize: () -> Unit,
    onReset: () -> Unit,
    isPastOrToday: Boolean,
    canChangeAvailability: Boolean,
    onOpen: () -> Unit,
    onMark: (SessionStatus, String) -> Unit,
    onAvailability: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val session: SessionEntity? = day.session
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        var picking by remember { mutableStateOf(false) }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("${day.date.dayOfWeek.long()} · ${day.date.dayOfMonth.twoDigits()}/${day.date.monthValue.twoDigits()}")
            Text(session?.name ?: "Descanso", style = MaterialTheme.typography.headlineMedium)
            if (day.special) Text("Mudado só nesta semana", style = MaterialTheme.typography.bodySmall, color = Palette.Lime)
            if (items.isNotEmpty()) SessionExerciseList(items, onOpenExercise)
            Spacer(Modifier.height(8.dp))
            if (session != null && isPastOrToday) {
                SheetAction(R.drawable.ic_play, "Abrir treino", Palette.Lime, onOpen)
                if (session.status != SessionStatus.DONE) {
                    SheetAction(R.drawable.ic_check, "Marcar como feito", Palette.Text) { onMark(SessionStatus.DONE, "Treino marcado como feito") }
                }
                if (session.status != SessionStatus.MISSED) {
                    SheetAction(R.drawable.ic_close, "Não treinei", Palette.Orange) { onMark(SessionStatus.MISSED, "Marcado como não treinado") }
                }
                if (session.status != SessionStatus.PLANNED && !day.unavailable) {
                    SheetAction(R.drawable.ic_timer, "Voltar para planejado", Palette.TextMuted) { onMark(SessionStatus.PLANNED, "Treino voltou para planejado") }
                }
            }
            if (day.closedLabel != null) {
                Text(
                    "${day.closedLabel}. O recálculo não põe treino neste dia (marcado no painel do treinador).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else if (canChangeAvailability) {
                if (day.unavailable) {
                    SheetAction(R.drawable.ic_calendar, "Estou disponível neste dia", Palette.Lime) { onAvailability(false) }
                } else {
                    SheetAction(R.drawable.ic_calendar, "Não vou poder treinar neste dia", Palette.TextMuted) { onAvailability(true) }
                    Text(
                        if (session?.status == SessionStatus.PLANNED) "O treino deste dia entra no recálculo da semana, e o dia fica de fora."
                        else "O recálculo não vai usar este dia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            if (canChangeThisWeek) {
                SectionLabel("Só nesta semana", Modifier.padding(top = 12.dp))
                if (picking) {
                    templates.forEach { summary ->
                        PickRow(
                            label = "${summary.template.code} · ${summary.template.name}",
                            selected = session?.templateId == summary.template.id,
                            onClick = { onPickThisWeek(summary.template.id) },
                        )
                    }
                    if (session != null) PickRow("Descanso", selected = false, onClick = { onPickThisWeek(null) })
                } else {
                    if (session?.templateId != null) SheetAction(R.drawable.ic_edit, "Mudar exercícios deste dia", Palette.Lime, onCustomize)
                    SheetAction(R.drawable.ic_swap, if (session == null) "Treinar neste dia" else "Trocar o treino deste dia", Palette.Text) { picking = true }
                    if (day.special) SheetAction(R.drawable.ic_arrow_back, "Voltar ao plano base", Palette.TextMuted, onReset)
                }
                Text(
                    "As outras semanas continuam seguindo o plano base.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDaySheet(row: PlanRow, templates: List<TemplateSummary>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Plano base")
            Text("Treino de ${row.day.long().lowercase()}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            templates.forEach { summary ->
                PickRow(
                    label = "${summary.template.code} · ${summary.template.name}",
                    selected = row.template?.id == summary.template.id,
                    onClick = { onPick(summary.template.id) },
                )
            }
            PickRow("Descanso", selected = row.template == null, onClick = { onPick(null) })
        }
    }
}

@Composable
private fun SheetAction(icon: Int, text: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Palette.SurfaceHigh, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(icon, null, tint = tint, size = 20.dp)
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Palette.Ground else Palette.SurfaceHigh,
        border = if (selected) BorderStroke(2.dp, Palette.Lime) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected) AppIcon(R.drawable.ic_check, "Selecionado", tint = Palette.Lime, size = 20.dp)
        }
    }
}
