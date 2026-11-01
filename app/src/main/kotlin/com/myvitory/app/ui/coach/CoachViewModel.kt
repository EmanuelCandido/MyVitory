package com.myvitory.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.sync.AthleteCloud
import com.myvitory.app.data.sync.SyncManager
import com.myvitory.app.data.sync.SyncOutcome
import com.myvitory.app.data.sync.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CoachUiState(
    val sync: SyncState = SyncState(),
    val cloud: AthleteCloud? = null,
    val busy: Boolean = false,
    /** Erro da última ação (conectar, gerar código...), para mostrar na tela. */
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModel(private val manager: SyncManager) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val cloud = manager.state.map { it.athleteId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else manager.link.observeAthlete(id)
    }

    val state: StateFlow<CoachUiState> = combine(manager.state, cloud, busy, error) { sync, athlete, isBusy, message ->
        CoachUiState(sync, athlete, isBusy, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachUiState())

    fun connect(name: String) = act("Não deu para conectar. Confira a internet.") {
        manager.link.connect(name)
        manager.link.newPairCode()
    }

    fun newCode() = act("Não deu para gerar o código. Confira a internet.") { manager.link.newPairCode() }

    fun removeCoach(uid: String) = act("Não deu para remover. Confira a internet.") { manager.link.removeCoach(uid) }

    fun setShareWeight(on: Boolean) = act("Não deu para mudar. Confira a internet.") {
        manager.link.setShareWeight(on)
        manager.runOnce()
    }

    fun syncNow() = act("Não deu para sincronizar. Confira a internet.") {
        if (manager.runOnce() == SyncOutcome.FAILED) error(manager.prefs.current().lastError ?: "falha")
    }

    fun disconnect() = act("Não deu para apagar da nuvem. Confira a internet e tente de novo.") { manager.link.disconnectAndErase() }

    fun dismissError() {
        error.value = null
    }

    private fun act(failure: String, block: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            runCatching { block() }.onFailure { error.value = failure }
            busy.value = false
        }
    }
}
