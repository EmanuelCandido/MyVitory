package com.myvitory.app.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.TodayLoads
import com.myvitory.app.domain.MuscleRecovery
import com.myvitory.app.domain.PRIMARY_LOAD
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class RecoveryUiState(
    val loading: Boolean = true,
    val nowMillis: Long = 0,
    val muscles: List<MuscleRecovery> = emptyList(),
    val today: TodayLoads? = null,
) {
    val recovering get() = muscles.filter { !it.ready }.sortedBy { it.percent }
    val ready get() = muscles.filter { it.ready }
    /** Músculos principais do treino de hoje que ainda não recuperaram. */
    val todayNotReady get() = today?.let { t ->
        val primaries = t.loads.filterValues { it >= PRIMARY_LOAD }.keys
        recovering.filter { it.muscle in primaries }
    }.orEmpty()
}

class RecoveryViewModel(private val coach: CoachRepository) : ViewModel() {
    /** A porcentagem muda com o tempo, então recalcula a cada minuto mesmo sem treino novo. */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val state: StateFlow<RecoveryUiState> =
        combine(coach.observeRecoveryEvents(), coach.observeTodayLoads(), ticker) { events, today, _ ->
            RecoveryUiState(loading = false, nowMillis = coach.now(), muscles = coach.recoveryNow(events), today = today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecoveryUiState())
}
