package com.myvitory.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.DayView
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val week: List<DayView> = emptyList(),
    val todayDay: DayView? = null,
    val preview: List<TemplateItemRow> = emptyList(),
    /** Primeiro dia que passou sem registro nesta semana. */
    val unmarked: DayView? = null,
    val next: DayView? = null,
    /** Treinos perdidos sobre os quais o usuário ainda não decidiu (recálculo pendente). */
    val pendingMissed: List<SessionEntity> = emptyList(),
    /** Dia de onde veio o treino de hoje, quando o recálculo o trouxe. */
    val movedFrom: LocalDate? = null,
) {
    val plannedCount get() = week.count { it.session != null }
    val doneCount get() = week.count { it.state == DayState.DONE || it.state == DayState.PARTIAL }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: TrainingRepository, coach: CoachRepository) : ViewModel() {
    private val today = repo.today()
    private val weekStart = WeekLogic.weekStartOf(today)

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            repo.ensureWeek(weekStart)
        }
    }

    private val week = repo.observeWeek(weekStart)

    private val preview = week
        .map { days -> days.firstOrNull { it.date == today }?.session?.let { it.templateId to it.mergedTemplateId } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            val templateId = ids?.first
            if (templateId == null) flowOf(emptyList()) else repo.observeSessionItems(templateId, ids.second)
        }

    val state: StateFlow<TodayUiState> = combine(week, preview, coach.observePendingMissed()) { days, items, pending ->
        TodayUiState(
            loading = false,
            today = today,
            week = days,
            todayDay = days.firstOrNull { it.date == today },
            preview = items,
            unmarked = days.firstOrNull { it.state == DayState.UNMARKED },
            next = days.firstOrNull { it.date.isAfter(today) && it.session?.status == SessionStatus.PLANNED },
            pendingMissed = pending,
            movedFrom = days.firstOrNull { it.session?.movedTo == today }?.date,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** Marca e devolve a ação de desfazer. */
    fun mark(session: SessionEntity, status: SessionStatus, onMarked: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val undo = repo.markSession(session.id, status) ?: return@launch
            onMarked { viewModelScope.launch { repo.undoMark(undo) } }
        }
    }

    /** A pergunta "Você treinou ontem?" abre sozinha só uma vez por treino enquanto o app está aberto. */
    fun shouldAsk(sessionId: Long): Boolean = askedThisRun.add(sessionId)

    private companion object {
        val askedThisRun = mutableSetOf<Long>()
    }
}
