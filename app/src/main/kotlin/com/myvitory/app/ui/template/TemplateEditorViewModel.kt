package com.myvitory.app.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.TemplateEntity
import com.myvitory.app.data.db.TemplateItemRow
import java.time.LocalDate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateEditorState(
    val template: TemplateEntity? = null,
    val items: List<TemplateItemRow> = emptyList(),
    val catalog: List<ExerciseEntity> = emptyList(),
    /** Dia que usa esta cópia, quando o treino vale só para uma semana. */
    val weekOnlyDay: LocalDate? = null,
)

class TemplateEditorViewModel(private val repo: TrainingRepository, private val templateId: Long) : ViewModel() {

    val state: StateFlow<TemplateEditorState> = combine(
        repo.observeTemplate(templateId),
        repo.observeTemplateItems(templateId),
        repo.observeExercises(),
        repo.observeWeekOnlyDay(templateId),
    ) { template, items, catalog, day -> TemplateEditorState(template, items, catalog, day.takeIf { template?.weekOnly == true }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateEditorState())

    fun rename(name: String) = launch { repo.renameTemplate(templateId, name) }

    fun add(exerciseId: String) = launch { repo.addExerciseToTemplate(templateId, exerciseId) }

    fun changeSets(item: TemplateItemRow, delta: Int) = launch { repo.updateTemplateItem(item.id, item.sets + delta, item.reps) }

    fun changeReps(item: TemplateItemRow, delta: Int) = launch { repo.updateTemplateItem(item.id, item.sets, item.reps + delta) }

    /** Descanso anda de 15 em 15 s. */
    fun changeRest(item: TemplateItemRow, delta: Int) = launch { repo.setItemRest(item.id, item.restSec + delta * REST_STEP_SEC) }

    fun setNote(item: TemplateItemRow, note: String) = launch { repo.setItemNote(item.id, note) }

    fun move(item: TemplateItemRow, delta: Int) = launch { repo.moveTemplateItem(item.id, delta) }

    fun remove(item: TemplateItemRow) = launch { repo.removeTemplateItem(item.id) }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch(NonCancellable) { if (repo.deleteTemplate(templateId)) onDeleted() }
    }

    // NonCancellable: o nome é salvo quando o campo perde o foco, o que acontece ao voltar, com o ViewModel já sendo descartado.
    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(NonCancellable) { block() }
    }

    companion object {
        const val REST_STEP_SEC = 15
    }
}
