package com.myvitory.app.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.DayView
import com.myvitory.app.data.PlanRow
import com.myvitory.app.data.TemplateSummary
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val today = repo.today()
    private val currentWeek = WeekLogic.weekStartOf(today)
    private val weekStart = MutableStateFlow(currentWeek)

    private val days = weekStart
        .onEach { start ->
            // Semanas passadas não ganham sessões novas: só mostram o que foi registrado.
            if (!start.isBefore(currentWeek)) repo.ensureWeek(start)
        }
        .flatMapLatest { repo.observeWeek(it) }

    val state: StateFlow<WeekUiState> =
        combine(weekStart, days, repo.observePlanBase(), repo.observeTemplates()) { start, d, plan, templates ->
            WeekUiState(today, start, d, plan, templates)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState(today, currentWeek))

    fun shiftWeek(weeks: Long) {
        weekStart.value = weekStart.value.plusWeeks(weeks)
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
}
