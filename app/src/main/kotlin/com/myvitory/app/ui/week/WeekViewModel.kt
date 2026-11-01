package com.myvitory.app.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.DayView
import com.myvitory.app.data.PlanRow
import com.myvitory.app.data.TemplateSummary
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeekUiState(
    val today: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = WeekLogic.weekStartOf(LocalDate.now()),
    val days: List<DayView> = emptyList(),
    val plan: List<PlanRow> = emptyList(),
    val templates: List<TemplateSummary> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(private val repo: TrainingRepository) : ViewModel() {
    /** Semanas para frente (+) ou para trás (−) a partir da semana de hoje, que muda sozinha quando o dia vira. */
    private val offset = MutableStateFlow(0L)

    private val shown = combine(repo.observeToday(), offset) { today, weeks -> today to WeekLogic.weekStartOf(today).plusWeeks(weeks) }
        .onEach { (today, start) ->
            // Semanas passadas não ganham sessões novas: só mostram o que foi registrado.
            if (!start.isBefore(WeekLogic.weekStartOf(today))) repo.ensureWeek(start)
        }

    val state: StateFlow<WeekUiState> = shown
        .flatMapLatest { (today, start) ->
            combine(repo.observeWeek(start), repo.observePlanBase(), repo.observeTemplates()) { d, plan, templates ->
                WeekUiState(today, start, d, plan, templates)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    fun shiftWeek(weeks: Long) {
        offset.value += weeks
    }

    fun mark(session: SessionEntity, status: SessionStatus, onMarked: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val undo = repo.markSession(session.id, status) ?: return@launch
            onMarked { viewModelScope.launch { repo.undoMark(undo) } }
        }
    }

    fun setUnavailable(date: LocalDate, unavailable: Boolean, onDone: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val undo = repo.setUnavailable(date, unavailable)
            onDone { viewModelScope.launch { repo.undoAvailability(undo) } }
        }
    }

    fun setPlanDay(day: DayOfWeek, templateId: Long?) {
        viewModelScope.launch { repo.setPlanDay(day, templateId) }
    }

    fun createTemplate(onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createTemplate()) }
    }

    /** Exercícios do treino de um dia, para ver sem abrir o treino. */
    fun itemsFor(session: SessionEntity): Flow<List<TemplateItemRow>> =
        session.templateId?.let { repo.observeSessionItems(it, session.mergedTemplateId) } ?: flowOf(emptyList())

    // ---- Só nesta semana ----

    fun setDayThisWeek(date: LocalDate, templateId: Long?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.setDayThisWeek(date, templateId)) }
    }

    fun customizeDay(session: SessionEntity, onReady: (templateId: Long?) -> Unit) {
        viewModelScope.launch { onReady(repo.customizeDayThisWeek(session.id)) }
    }

    fun resetDay(date: LocalDate, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(repo.resetDayToPlan(date)) }
    }
}
