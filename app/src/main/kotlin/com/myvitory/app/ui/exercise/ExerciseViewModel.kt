package com.myvitory.app.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.ExerciseTips
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.CachedVideoEntity
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.PinnedVideoEntity
import com.myvitory.app.data.video.VideoError
import com.myvitory.app.data.video.VideoRepository
import com.myvitory.app.domain.Alternatives
import com.myvitory.app.domain.ExerciseRef
import com.myvitory.app.domain.VideoLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Um vídeo na lista da tela: o fixado ou um resultado de busca. */
data class VideoItem(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationSec: Int?,
    val thumbnailUrl: String?,
    val pinned: Boolean,
)

private data class SearchState(
    val results: List<CachedVideoEntity> = emptyList(),
    val searching: Boolean = false,
    val error: VideoError? = null,
)

data class ExerciseUiState(
    val exercise: ExerciseEntity? = null,
    val tips: List<String> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val selected: Int = 0,
    val searching: Boolean = false,
    val error: VideoError? = null,
    val searchEnabled: Boolean = false,
    val alternatives: List<ExerciseEntity> = emptyList(),
    /** Aberto a partir do treino em andamento: mostra "Trocar por". */
    val canSwap: Boolean = false,
    val swapInPlan: Boolean = false,
) {
    val current: VideoItem? get() = videos.getOrNull(selected)
}

class ExerciseViewModel(
    private val training: TrainingRepository,
    private val videos: VideoRepository,
    private val exerciseId: String,
    private val sessionId: Long?,
) : ViewModel() {
    private val search = MutableStateFlow(SearchState())
    private val selected = MutableStateFlow(0)
    private val swapInPlan = MutableStateFlow(false)

    private val catalog = training.observeExercises()

    private val canSwap = if (sessionId == null) flowOf(false) else
        training.observeWorkout(sessionId).map { w -> w?.exercises?.any { ex -> ex.exerciseId == exerciseId && ex.sets.any { !it.done } } == true }

    private val videoList = combine(videos.observePinned(exerciseId), search) { pinned, s -> merge(pinned, s.results) }

    val state: StateFlow<ExerciseUiState> = combine(
        combine(catalog, videoList, search, selected) { all, list, s, sel -> Quad(all, list, s, sel) },
        canSwap,
        swapInPlan,
    ) { (all, list, s, sel), swap, inPlan ->
        val exercise = all.firstOrNull { it.id == exerciseId }
        ExerciseUiState(
            exercise = exercise,
            tips = ExerciseTips.forExercise(exerciseId),
            videos = list,
            selected = sel.coerceIn(0, (list.size - 1).coerceAtLeast(0)),
            searching = s.searching,
            error = s.error,
            searchEnabled = videos.searchEnabled,
            alternatives = exercise?.let { alternativesFor(it, all) }.orEmpty(),
            canSwap = swap,
            swapInPlan = inPlan,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseUiState())

    init {
        loadVideos(forceRefresh = false)
    }

    fun loadVideos(forceRefresh: Boolean) {
        viewModelScope.launch {
            val exercise = catalog.first().firstOrNull { it.id == exerciseId } ?: return@launch
            search.update { it.copy(searching = true) }
            val lookup = videos.search(VideoLinks.queryFor(exercise.name), forceRefresh)
            search.value = SearchState(lookup.videos, searching = false, error = lookup.error)
            if (forceRefresh) selected.value = 0
        }
    }

    fun select(index: Int) {
        selected.value = index
    }

    /** Vídeo que não toca embutido (bloqueado pelo dono, removido): pula para o próximo. */
    fun nextVideo() {
        val size = state.value.videos.size
        if (size > 1) selected.update { (it + 1) % size }
    }

    fun togglePin(item: VideoItem) {
        viewModelScope.launch {
            if (item.pinned) videos.unpin(exerciseId) else videos.pin(exerciseId, item.videoId, item.title, item.channel)
            selected.value = 0
        }
    }

    /** "Colar link": fixa um vídeo qualquer do YouTube para este exercício. Funciona sem chave da API. */
    fun pinLink(text: String): Boolean {
        val id = VideoLinks.parseId(text) ?: return false
        viewModelScope.launch {
            videos.pin(exerciseId, id, "Vídeo escolhido por você", "")
            selected.value = 0
        }
        return true
    }

    fun setSwapInPlan(value: Boolean) {
        swapInPlan.value = value
    }

    fun swap(to: ExerciseEntity, onSwapped: (undo: () -> Unit) -> Unit) {
        val session = sessionId ?: return
        viewModelScope.launch {
            val undo = training.swapExercise(session, exerciseId, to.id, swapInPlan.value) ?: return@launch
            onSwapped { viewModelScope.launch { training.undoSwap(undo) } }
        }
    }

    private fun merge(pinned: PinnedVideoEntity?, results: List<CachedVideoEntity>): List<VideoItem> {
        val fromSearch = results.filter { it.videoId != pinned?.videoId }.map {
            VideoItem(it.videoId, it.title, it.channel, it.durationSec, it.thumbnailUrl, pinned = false)
        }
        val fixed = pinned?.let { p ->
            val match = results.firstOrNull { it.videoId == p.videoId }
            VideoItem(
                p.videoId,
                match?.title ?: p.title,
                match?.channel ?: p.channel,
                match?.durationSec,
                match?.thumbnailUrl ?: "https://i.ytimg.com/vi/${p.videoId}/mqdefault.jpg",
                pinned = true,
            )
        }
        return listOfNotNull(fixed) + fromSearch
    }

    private fun alternativesFor(target: ExerciseEntity, all: List<ExerciseEntity>): List<ExerciseEntity> {
        val byId = all.associateBy { it.id }
        return Alternatives.rank(target.ref(), all.map { it.ref() }).mapNotNull { byId[it.id] }
    }

    private fun ExerciseEntity.ref() = ExerciseRef(id, primary, secondary, equipment)

    private data class Quad(
        val all: List<ExerciseEntity>,
        val list: List<VideoItem>,
        val search: SearchState,
        val selected: Int,
    )
}
