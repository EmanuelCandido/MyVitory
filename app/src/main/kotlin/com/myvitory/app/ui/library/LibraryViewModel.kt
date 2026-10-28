package com.myvitory.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.CachedVideoEntity
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.video.VideoError
import com.myvitory.app.data.video.VideoRepository
import com.myvitory.app.domain.Muscle
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filtros da barra de chips: grupos como o usuário pensa, não músculo por músculo. */
enum class MuscleGroup(val label: String, val muscles: Set<Muscle>) {
    CHEST("Peito", setOf(Muscle.CHEST)),
    BACK("Costas", setOf(Muscle.BACK)),
    LEGS("Pernas", setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES)),
    SHOULDERS("Ombros", setOf(Muscle.SHOULDERS)),
    ARMS("Braços", setOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS)),
    ABS("Abdômen", setOf(Muscle.ABS)),
}

data class LibraryExercise(val exercise: ExerciseEntity, val inPlan: Boolean)

data class VideoSearchUi(
    val query: String = "",
    val results: List<CachedVideoEntity> = emptyList(),
    val searching: Boolean = false,
    val error: VideoError? = null,
)

data class LibraryUiState(
    val query: String = "",
    val group: MuscleGroup? = null,
    val tab: Int = 0,
    val exercises: List<LibraryExercise> = emptyList(),
    val videos: VideoSearchUi = VideoSearchUi(),
    val searchEnabled: Boolean = false,
)

class LibraryViewModel(training: TrainingRepository, private val videos: VideoRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow<MuscleGroup?>(null)
    private val tab = MutableStateFlow(0)
    private val videoSearch = MutableStateFlow(VideoSearchUi())

    private val filters = combine(query, group, tab) { q, g, t -> Triple(q, g, t) }

    val state: StateFlow<LibraryUiState> = combine(
        training.observeExercises(),
        training.observeExerciseIdsInPlans(),
        filters,
        videoSearch,
    ) { all, inPlans, (q, g, t), v ->
        val planIds = inPlans.toSet()
        val needle = normalize(q)
        val list = all
            .filter { g == null || it.primary.any { m -> m in g.muscles } }
            .filter { needle.isEmpty() || normalize(it.name).contains(needle) || it.primary.any { m -> normalize(m.label).contains(needle) } }
            .map { LibraryExercise(it, it.id in planIds) }
            .sortedWith(compareByDescending<LibraryExercise> { it.inPlan }.thenBy { it.exercise.name })
        LibraryUiState(q, g, t, list, v, videos.searchEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(text: String) {
        query.value = text
    }

    fun setGroup(value: MuscleGroup?) {
        group.value = if (group.value == value) null else value
    }

    fun setTab(value: Int) {
        tab.value = value
    }

    /** Busca de vídeos só quando o usuário pede (Enter ou botão): cada busca nova gasta 1% da cota diária. */
    fun searchVideos() {
        val q = query.value.trim()
        if (q.isEmpty()) return
        tab.value = 1
        videoSearch.update { it.copy(query = q, searching = true, error = null) }
        viewModelScope.launch {
            val lookup = videos.search(q)
            videoSearch.value = VideoSearchUi(q, lookup.videos, searching = false, error = lookup.error)
        }
    }

    private fun normalize(text: String) =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
