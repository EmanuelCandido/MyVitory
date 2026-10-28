package com.myvitory.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.WorkoutView
import com.myvitory.app.data.db.SetLogEntity
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.timer.RestTimer
import com.myvitory.app.timer.RestTimerHandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(
    private val repo: TrainingRepository,
    private val restTimer: RestTimerHandle,
    private val sessionId: Long,
) : ViewModel() {

    init {
        viewModelScope.launch { repo.startWorkout(sessionId) }
    }

    val workout: StateFlow<WorkoutView?> =
        repo.observeWorkout(sessionId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** O descanso vive fora da tela (serviço com notificação) para continuar com o celular bloqueado. */
    val rest: StateFlow<RestTimer?> = restTimer.state

    /** Marcar uma série inicia o descanso sozinho. */
    fun toggleSet(log: SetLogEntity, exerciseName: String) {
        val nowDone = !log.done
        viewModelScope.launch { repo.updateSet(log.copy(done = nowDone)) }
        if (nowDone && log.restSec > 0) restTimer.start(log.restSec, "Próxima: $exerciseName") else restTimer.skip()
    }

    fun setWeight(log: SetLogEntity, kg: Double) {
        if (kg == log.weightKg || kg < 0) return
        viewModelScope.launch { repo.updateSet(log.copy(weightKg = kg)) }
    }

    fun setReps(log: SetLogEntity, reps: Int) {
        if (reps == log.reps || reps < 0) return
        viewModelScope.launch { repo.updateSet(log.copy(reps = reps)) }
    }

    fun addSet(exerciseId: String) {
        viewModelScope.launch { repo.addSet(sessionId, exerciseId) }
    }

    fun adjustRest(deltaSec: Int) = restTimer.adjust(deltaSec)

    fun skipRest() = restTimer.skip()

    fun restReachedZero(timer: RestTimer) = restTimer.complete(timer)

    /** Devolve o status salvo, ou null quando nenhuma série foi marcada (a tela pergunta o que fazer). */
    fun finish(onResult: (SessionStatus?) -> Unit) {
        viewModelScope.launch {
            restTimer.skip()
            onResult(repo.finishWorkout(sessionId))
        }
    }

    fun markWithoutSets(status: SessionStatus, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.markSession(sessionId, status)
            onDone()
        }
    }
}
