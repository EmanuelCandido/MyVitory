package com.myvitory.app.ui.template

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.theme.Palette

@Composable
fun TemplateEditorScreen(templateId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "template-$templateId") { TemplateEditorViewModel(it.training, templateId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar") }
            Text("Editar treino", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                state.template?.let { template ->
                    NameField(template.code, template.name, onCommit = vm::rename)
                }
            }
            item {
                SectionLabel("Exercícios · ${state.items.size}", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp))
            }
            items(state.items, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    onSets = { vm.changeSets(item, it) },
                    onReps = { vm.changeReps(item, it) },
                    onRemove = { vm.remove(item) },
                )
            }
            item {
                Surface(
                    onClick = { adding = true },
                    shape = RoundedCornerShape(16.dp),
                    color = Palette.Ground,
                    border = BorderStroke(1.dp, Palette.LineStrong),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(R.drawable.ic_plus, null, tint = Palette.Lime, size = 18.dp)
                        Text("  Adicionar exercício", style = MaterialTheme.typography.titleMedium, color = Palette.Lime)
                    }
                }
            }
        }
    }

    if (adding) {
        AddExerciseSheet(
            catalog = state.catalog,
            alreadyIn = state.items.map { it.exerciseId }.toSet(),
            onPick = { adding = false; vm.add(it.id) },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun NameField(code: String, name: String, onCommit: (String) -> Unit) {
    var text by remember(name) { mutableStateOf(name) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Nome do treino $code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Lime,
            unfocusedBorderColor = Palette.LineStrong,
            focusedLabelColor = Palette.Lime,
            cursorColor = Palette.Lime,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onCommit(text) },
    )
}

@Composable
private fun ItemCard(item: TemplateItemRow, onSets: (Int) -> Unit, onReps: (Int) -> Unit, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    AppIcon(R.drawable.ic_trash, "Remover ${item.exerciseName}", tint = Palette.TextMuted, size = 20.dp)
                }
            }
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("Séries", item.sets, onSets, Modifier.weight(1f))
                Stepper("Reps", item.reps, onReps, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onDelta: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .border(1.dp, Palette.LineStrong, CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clickable { onDelta(-1) }, Alignment.Center) {
                AppIcon(R.drawable.ic_minus, "Diminuir $label", size = 18.dp)
            }
            Text("$value", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Box(Modifier.size(48.dp).clickable { onDelta(1) }, Alignment.Center) {
                AppIcon(R.drawable.ic_plus, "Aumentar $label", size = 18.dp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseSheet(
    catalog: List<ExerciseEntity>,
    alreadyIn: Set<String>,
    onPick: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(catalog, query) {
        val q = query.trim().lowercase()
        catalog
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.primary.any { m -> m.label.lowercase().contains(q) } }
            .groupBy { it.primary.firstOrNull()?.label ?: "Outros" }
            .toSortedMap()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding().imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar exercício ou músculo") },
                singleLine = true,
                leadingIcon = { AppIcon(R.drawable.ic_search, null, tint = Palette.TextMuted, size = 20.dp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Lime,
                    unfocusedBorderColor = Palette.LineStrong,
                    cursorColor = Palette.Lime,
                ),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
                filtered.forEach { (group, exercises) ->
                    item(key = "h-$group") { SectionLabel(group, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) }
                    items(exercises, key = { it.id }) { exercise ->
                        val inTemplate = exercise.id in alreadyIn
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(exercise) }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    exercise.equipment + if (inTemplate) " · já está no treino" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextMuted,
                                )
                            }
                            AppIcon(R.drawable.ic_plus, "Adicionar ${exercise.name}", tint = Palette.Lime, size = 20.dp)
                            Box(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
    }
}
