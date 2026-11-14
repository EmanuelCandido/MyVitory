package com.myvitory.app.ui.template

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.myvitory.app.R
import com.myvitory.app.data.ExerciseMedia
import com.myvitory.app.data.ExerciseTips
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.ExerciseThumb
import com.myvitory.app.ui.components.PrimaryButton
import com.myvitory.app.ui.components.SecondaryButton
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.theme.Mono
import com.myvitory.app.ui.theme.Palette

/**
 * Escolher um exercício para o treino. Tocar na linha mostra o movimento antes de adicionar;
 * o "+" adiciona direto. [previewId] fica fora da folha para sobreviver à ida até o vídeo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(
    catalog: List<ExerciseEntity>,
    alreadyIn: Set<String>,
    previewId: String?,
    onPreview: (String?) -> Unit,
    onPick: (ExerciseEntity) -> Unit,
    onOpenExercise: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(catalog, query) {
        val q = query.trim().lowercase()
        catalog
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.primary.any { m -> m.label.lowercase().contains(q) } }
            .groupBy { it.primary.firstOrNull()?.label ?: "Outros" }
            .toSortedMap()
    }
    val preview = previewId?.let { id -> catalog.firstOrNull { it.id == id } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxHeight(0.85f).navigationBarsPadding().imePadding()) {
            if (preview != null) {
                ExercisePreview(
                    exercise = preview,
                    inTemplate = preview.id in alreadyIn,
                    onBack = { onPreview(null) },
                    onAdd = { onPick(preview) },
                    onOpenVideo = { onOpenExercise(preview.id) },
                )
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar exercício ou músculo") },
                singleLine = true,
                leadingIcon = { AppIcon(R.drawable.ic_search, null, tint = Palette.TextMuted, size = 20.dp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Volt,
                    unfocusedBorderColor = Palette.LineStrong,
                    cursorColor = Palette.Volt,
                ),
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)) {
                filtered.forEach { (group, exercises) ->
                    item(key = "h-$group", contentType = "header") { SectionLabel(group, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) }
                    items(exercises, key = { it.id }, contentType = { "exercise" }) { exercise ->
                        val inTemplate = exercise.id in alreadyIn
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreview(exercise.id) }
                                .padding(start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ExerciseThumb(exercise.id, 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    (exercise.primary.map { it.label } + exercise.equipment).joinToString(" · ") +
                                        if (inTemplate) " · já está no treino" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextMuted,
                                )
                            }
                            IconButton(onClick = { onPick(exercise) }, modifier = Modifier.size(48.dp)) {
                                AppIcon(R.drawable.ic_plus, "Adicionar ${exercise.name}", tint = Palette.Volt, size = 20.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExercisePreview(
    exercise: ExerciseEntity,
    inTemplate: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar para a lista") }
            Text(exercise.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                exercise.primary.forEach { Chip(it.label, highlight = true) }
                exercise.secondary.forEach { Chip(it.label, highlight = false) }
                Chip(exercise.equipment, highlight = false)
            }
            ExerciseMedia.gifUrl(exercise.id)?.let { gif ->
                // Aqui o GIF anima: é um só, e é o que a pessoa quer ver antes de escolher.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White), Alignment.Center) {
                    AsyncImage(
                        model = gif,
                        contentDescription = "Animação de ${exercise.name}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f),
                    )
                }
            }
            val tips = ExerciseTips.forExercise(exercise.id)
            if (tips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Pontos-chave")
                    tips.forEachIndexed { i, tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Mono), color = Palette.Volt, modifier = Modifier.width(18.dp))
                            Text(tip, style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.88f))
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (inTemplate) "Adicionar de novo" else "Adicionar ao treino", onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
            SecondaryButton("Ver vídeo e mais detalhes", onOpenVideo, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (highlight) Palette.Volt else Color.Transparent,
        border = if (highlight) null else BorderStroke(1.dp, Palette.LineStrong),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) Palette.Ground else Palette.Text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
