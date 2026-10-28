package com.myvitory.app.ui.template

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.TemplateEntity
import com.myvitory.app.data.db.TemplateItemRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateEditorState(
    val template: TemplateEntity? = null,
    val items: List<TemplateItemRow> = emptyList(),
    val catalog: List<ExerciseEntity> = emptyList(),
)

class TemplateEditorViewModel(private val repo: TrainingRepository, private val templateId: Long) : ViewModel() {

    val state: StateFlow<TemplateEditorState> = combine(
        repo.observeTemplate(templateId),
        repo.observeTemplateItems(templateId),
        repo.observeExercises(),
    ) { template, items, catalog -> TemplateEditorState(template, items, catalog) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateEditorState())

    fun rename(name: String) = launch { repo.renameTemplate(templateId, name) }

    fun add(exerciseId: String) = launch { repo.addExerciseToTemplate(templateId, exerciseId) }

    fun changeSets(item: TemplateItemRow, delta: Int) = launch { repo.updateTemplateItem(item.id, item.sets + delta, item.reps) }

    fun changeReps(item: TemplateItemRow, delta: Int) = launch { repo.updateTemplateItem(item.id, item.sets, item.reps + delta) }

    fun remove(item: TemplateItemRow) = launch { repo.removeTemplateItem(item.id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
