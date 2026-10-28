package com.myvitory.app.ui.replan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.ReplanData
import com.myvitory.app.domain.planner.Planner
import com.myvitory.app.domain.planner.Proposal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReplanUiState(
    val loading: Boolean = true,
    val data: ReplanData? = null,
    val tab: Int = 0,
    val busy: Boolean = false,
) {
    val proposal: Proposal? get() = data?.let { if (tab == 1) it.result.alternative ?: it.result.best else it.result.best }
    val explanation: Planner.Explanation? get() = data?.let { d -> proposal?.let { Planner.explain(d.input, it) } }
    val hasAlternative get() = data?.result?.alternative != null
    /** A proposta não coloca nenhum treino perdido na semana: só resta pular. */
    val onlySkip get() = data != null && proposal?.let { p -> data.missed.none { m -> p.assignment.values.any { it.sessionId == m.id } } } == true
}

class ReplanViewModel(private val coach: CoachRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReplanUiState())
    val state: StateFlow<ReplanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val data = coach.buildReplan()
            _state.update { it.copy(loading = false, data = data) }
        }
    }

    fun selectTab(tab: Int) = _state.update { it.copy(tab = tab) }

    fun apply(onApplied: (undo: () -> Unit) -> Unit) {
        val current = _state.value
        val data = current.data ?: return
        val proposal = current.proposal ?: return
        if (current.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val undo = coach.apply(data, proposal)
            onApplied { viewModelScope.launch { coach.undo(undo) } }
        }
    }

    fun skip(onSkipped: (undo: () -> Unit) -> Unit) {
        val data = _state.value.data ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val undo = coach.dismiss(data.missed)
            onSkipped { viewModelScope.launch { coach.undo(undo) } }
        }
    }
}
