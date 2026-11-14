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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.myvitory.app.ui.components.ExerciseThumb
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.formatClock
import com.myvitory.app.ui.components.long
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.Palette

@Composable
fun TemplateEditorScreen(templateId: Long, onBack: () -> Unit, onOpenExercise: (String) -> Unit) {
    val vm = appViewModel(key = "template-$templateId") { TemplateEditorViewModel(it.training, templateId) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Salvos: ao voltar do vídeo, a folha de adicionar reabre no mesmo exercício.
    var adding by rememberSaveable { mutableStateOf(false) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar") }
            Text("EDITAR TREINO", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            // Cópia "só nesta semana" some sozinha quando o dia passa; só o treino do plano base é apagado aqui.
            if (state.template?.weekOnly == false) {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(48.dp)) {
                    AppIcon(R.drawable.ic_trash, "Apagar treino", tint = Palette.TextMuted, size = 20.dp)
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                state.template?.let { template ->
                    NameField(template.code, template.name, onCommit = vm::rename)
                    if (template.weekOnly) {
                        val day = state.weekOnlyDay
                        Text(
                            (if (day != null) "Só para ${day.dayOfWeek.long().lowercase()}, ${day.dayOfMonth.twoDigits()}/${day.monthValue.twoDigits()}. " else "Só para um dia desta semana. ") +
                                "O plano base não muda.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Volt,
                            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                        )
                    }
                }
            }
            item {
                SectionLabel("Exercícios · ${state.items.size}", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp))
            }
            items(state.items, key = { it.id }) { item ->
                val index = state.items.indexOf(item)
                ItemCard(
                    item = item,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.items.lastIndex,
                    onMove = { vm.move(item, it) },
                    onSets = { vm.changeSets(item, it) },
                    onReps = { vm.changeReps(item, it) },
                    onRest = { vm.changeRest(item, it) },
                    onNote = { vm.setNote(item, it) },
                    onRemove = { vm.remove(item) },
                    onOpen = { onOpenExercise(item.exerciseId) },
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
                        AppIcon(R.drawable.ic_plus, null, tint = Palette.Volt, size = 18.dp)
                        Text("  Adicionar exercício", style = MaterialTheme.typography.titleMedium, color = Palette.Volt)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar ${state.template?.name.orEmpty()}?") },
            text = {
                Text(
                    "Os dias do plano com este treino viram descanso e ele sai dos próximos dias. " +
                        "Os treinos que você já fez continuam no histórico.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Apagar", color = Palette.Flame) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            containerColor = Palette.SurfaceHigh,
        )
    }

    if (adding) {
        AddExerciseSheet(
            catalog = state.catalog,
            alreadyIn = state.items.map { it.exerciseId }.toSet(),
            previewId = previewId,
            onPreview = { previewId = it },
            onPick = { adding = false; previewId = null; vm.add(it.id) },
            onOpenExercise = onOpenExercise,
            onDismiss = { adding = false; previewId = null },
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
            focusedBorderColor = Palette.Volt,
            unfocusedBorderColor = Palette.LineStrong,
            focusedLabelColor = Palette.Volt,
            cursorColor = Palette.Volt,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onCommit(text) },
    )
}

@Composable
private fun ItemCard(
    item: TemplateItemRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onSets: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onRest: (Int) -> Unit,
    onNote: (String) -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Foto e nome abrem o exercício (movimento, vídeo, pontos-chave).
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExerciseThumb(item.exerciseId, 40.dp)
                    Text(item.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(40.dp)) {
                    AppIcon(R.drawable.ic_chevron_up, "Subir ${item.exerciseName}", tint = if (canMoveUp) Palette.TextMuted else Palette.Neutral, size = 20.dp)
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(40.dp)) {
                    AppIcon(R.drawable.ic_chevron_down, "Descer ${item.exerciseName}", tint = if (canMoveDown) Palette.TextMuted else Palette.Neutral, size = 20.dp)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    AppIcon(R.drawable.ic_trash, "Remover ${item.exerciseName}", tint = Palette.TextMuted, size = 20.dp)
                }
            }
            Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Séries", "${item.sets}", onSets, Modifier.weight(1f))
                Stepper("Reps", "${item.reps}", onReps, Modifier.weight(1f))
                Stepper("Descanso", formatClock(item.restSec.toLong()), onRest, Modifier.weight(1.2f))
            }
            NoteField(item.note, onNote, Modifier.padding(end = 12.dp))
        }
    }
}

/** Observação do exercício, salva quando o campo perde o foco (como o nome do treino). */
@Composable
private fun NoteField(note: String, onCommit: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(note) { mutableStateOf(note) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(com.myvitory.app.data.MAX_TEXT) },
        placeholder = { Text("Observação (ex.: descer em 3 s)", color = Palette.TextFaint) },
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Volt,
            unfocusedBorderColor = Palette.Line,
            cursorColor = Palette.Volt,
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused && text != note) onCommit(text) },
    )
}

@Composable
private fun Stepper(label: String, value: String, onDelta: (Int) -> Unit, modifier: Modifier = Modifier) {
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
            Box(Modifier.size(40.dp, 48.dp).clickable { onDelta(-1) }, Alignment.Center) {
                AppIcon(R.drawable.ic_minus, "Diminuir $label", size = 18.dp)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            Box(Modifier.size(40.dp, 48.dp).clickable { onDelta(1) }, Alignment.Center) {
                AppIcon(R.drawable.ic_plus, "Aumentar $label", size = 18.dp)
            }
        }
    }
}

