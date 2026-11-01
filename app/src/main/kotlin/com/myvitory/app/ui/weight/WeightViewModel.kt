package com.myvitory.app.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.WeightRepository
import com.myvitory.app.data.settings.SettingsRepository
import com.myvitory.app.domain.GoalProgress
import com.myvitory.app.domain.Weighing
import com.myvitory.app.domain.WeightForecast
import com.myvitory.app.domain.WeightGoal
import com.myvitory.app.domain.WeightLogic
import com.myvitory.app.domain.WeightTrend
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeightUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    /** Da mais antiga para a mais recente. */
    val weights: List<Weighing> = emptyList(),
    val trend: WeightTrend? = null,
    val forecast: WeightForecast? = null,
    val goal: WeightGoal? = null,
    val progress: GoalProgress? = null,
    val daysSinceLast: Long? = null,
) {
    val last get() = weights.lastOrNull()
    val due get() = !loading && (daysSinceLast ?: Long.MAX_VALUE) >= WeightLogic.REMIND_AFTER_DAYS
}

class WeightViewModel(private val repo: WeightRepository, private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<WeightUiState> = combine(repo.observe(), settings.settings) { weights, prefs ->
        val today = repo.today()
        val goal = prefs.weightGoal
        WeightUiState(
            loading = false,
            today = today,
            weights = weights,
            trend = WeightLogic.trend(weights),
            forecast = WeightLogic.forecast(weights, today),
            goal = goal,
            progress = goal?.let { WeightLogic.progress(it, weights, today) },
            daysSinceLast = WeightLogic.daysSinceLast(weights, today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    /** Grava e devolve a ação de desfazer. */
    fun record(date: LocalDate, kg: Double, onSaved: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            val before = repo.record(date, kg)
            onSaved { viewModelScope.launch { repo.restore(date, before) } }
        }
    }

    fun delete(weighing: Weighing, onDeleted: (undo: () -> Unit) -> Unit) {
        viewModelScope.launch {
            repo.delete(weighing.date)
            onDeleted { viewModelScope.launch { repo.restore(weighing.date, weighing) } }
        }
    }

    /**
     * A meta parte do peso atual (a tela só oferece meta depois da primeira pesagem).
     * Mudar só o prazo mantém o ponto de partida, para a barra não zerar.
     */
    fun setGoal(targetKg: Double, by: LocalDate?) {
        viewModelScope.launch {
            val start = state.value.goal?.takeIf { it.targetKg == targetKg }?.startKg ?: repo.latest()?.kg ?: return@launch
            settings.setWeightGoal(WeightGoal(targetKg, start, by))
        }
    }

    fun clearGoal() {
        viewModelScope.launch { settings.setWeightGoal(null) }
    }
}
