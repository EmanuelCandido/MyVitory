package com.myvitory.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.DayView
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.WeightRepository
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.domain.WeightLogic
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    /** Dias desde a última pesagem (null se nunca pesou). */
    val daysSinceWeight: Long? = null,
    /** Um mês ou mais sem pesar. */
    val weightDue: Boolean = false,
) {
    val plannedCount get() = week.count { it.session != null }
    val doneCount get() = week.count { it.state == DayState.DONE || it.state == DayState.PARTIAL }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val repo: TrainingRepository, coach: CoachRepository, weights: WeightRepository) : ViewModel() {
    /** Segue a virada do dia: o app aberto de um dia para o outro mostra o dia novo (e cria a semana nova). */
    private val date = repo.observeToday().onEach { day ->
        repo.seedIfEmpty()
        repo.ensureWeek(WeekLogic.weekStartOf(day))
    }

    val state: StateFlow<TodayUiState> = date.flatMapLatest { today -> stateFor(today, coach, weights) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun stateFor(today: LocalDate, coach: CoachRepository, weights: WeightRepository): Flow<TodayUiState> {
        val week = repo.observeWeek(WeekLogic.weekStartOf(today))
        val preview = week
            .map { days -> days.firstOrNull { it.date == today }?.session?.let { it.templateId to it.mergedTemplateId } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                val templateId = ids?.first
                if (templateId == null) flowOf(emptyList()) else repo.observeSessionItems(templateId, ids.second)
            }
        return combine(week, preview, coach.observePendingMissed(), weights.observe()) { days, items, pending, weighings ->
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
                daysSinceWeight = WeightLogic.daysSinceLast(weighings, today),
                weightDue = WeightLogic.isDue(weighings, today),
            )
        }
    }

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
