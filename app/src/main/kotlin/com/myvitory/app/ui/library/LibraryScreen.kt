package com.myvitory.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.myvitory.app.R
import com.myvitory.app.data.db.CachedVideoEntity
import com.myvitory.app.data.video.VideoError
import com.myvitory.app.domain.VideoLinks
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.theme.Palette
import com.myvitory.app.ui.video.YouTubePlayerBox

@Composable
fun LibraryScreen(contentPadding: PaddingValues, onOpenExercise: (String) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.training, it.videos) }
    val state by vm.state.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<CachedVideoEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Exercícios", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Buscar exercício ou vídeo") },
                singleLine = true,
                leadingIcon = { AppIcon(R.drawable.ic_search, null, tint = Palette.TextMuted, size = 20.dp) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { AppIcon(R.drawable.ic_close, "Limpar busca", tint = Palette.TextMuted, size = 18.dp) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (state.tab == 1) vm.searchVideos() }),
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Lime,
                    unfocusedBorderColor = Palette.LineStrong,
                    cursorColor = Palette.Lime,
                    focusedContainerColor = Palette.Surface,
                    unfocusedContainerColor = Palette.Surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("Todos", state.group == null) { vm.setGroup(null) }
            MuscleGroup.entries.forEach { g -> FilterChip(g.label, state.group == g) { vm.setGroup(g) } }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            UnderlineTab("Exercícios · ${state.exercises.size}", state.tab == 0) { vm.setTab(0) }
            UnderlineTab("Vídeos", state.tab == 1) { vm.setTab(1) }
        }
        HorizontalDivider(color = Palette.Surface)

        if (state.tab == 0) {
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                items(state.exercises, key = { it.exercise.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable { onOpenExercise(item.exercise.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Surface), Alignment.Center) {
                            AppIcon(R.drawable.ic_play, null, tint = Palette.TextMuted, size = 18.dp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.exercise.name, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    (item.exercise.primary.map { it.label } + item.exercise.equipment).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Palette.TextMuted,
                                )
                                if (item.inPlan) Text("NO SEU PLANO", style = MaterialTheme.typography.labelSmall, color = Palette.Lime)
                            }
                        }
                        AppIcon(R.drawable.ic_chevron_right, null, tint = Palette.Neutral, size = 18.dp)
                    }
                    HorizontalDivider(color = Palette.Surface)
                }
            }
        } else {
            VideoResults(state, onSearch = vm::searchVideos, onPlay = { playing = it })
        }
    }

    playing?.let { video ->
        Dialog(onDismissRequest = { playing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(color = Palette.Ground, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column {
                    YouTubePlayerBox(video.videoId, Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)))
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(video.channel, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                        TextButton(onClick = { playing = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Fechar", color = Palette.Lime, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoResults(state: LibraryUiState, onSearch: () -> Unit, onPlay: (CachedVideoEntity) -> Unit) {
    val search = state.videos
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            when {
                !state.searchEnabled -> Note("Busca de vídeos desligada: falta a chave da YouTube API. Dentro de cada exercício dá para colar o link de um vídeo.")
                state.query.isBlank() -> Note("Digite o nome do exercício e toque em buscar no teclado.")
                state.query.trim() != search.query && !search.searching -> TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) {
                    AppIcon(R.drawable.ic_search, null, tint = Palette.Lime, size = 18.dp)
                    Text("  Buscar vídeos de “${state.query.trim()}”", color = Palette.Lime, fontWeight = FontWeight.Bold)
                }
                search.searching -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Palette.Lime) }
                search.error == VideoError.Offline && search.results.isEmpty() -> Note("Sem internet.")
                search.error == VideoError.Quota && search.results.isEmpty() -> Note("Limite diário de buscas atingido. Tente amanhã.")
                search.results.isEmpty() -> Note("Nenhum vídeo encontrado.")
                else -> SectionLabel("Do YouTube · tocam aqui no app", Modifier.padding(vertical = 6.dp))
            }
        }
        if (state.query.trim() == search.query) {
            items(search.results, key = { it.id }) { video ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPlay(video) }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Palette.Surface)) {
                        AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        video.durationSec?.let {
                            Text(
                                VideoLinks.formatDuration(it),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Ground.copy(alpha = 0.8f)).padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(video.channel, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Palette.Text else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Palette.LineStrong),
        modifier = Modifier.heightIn(min = 36.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 14.dp), Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) Palette.Ground else Palette.Text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun UnderlineTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .heightIn(min = 46.dp)
            .width(IntrinsicSize.Max)
            .clickable(onClick = onClick)
            .semantics { role = Role.Tab; this.selected = selected },
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            color = if (selected) Palette.Text else Palette.TextMuted,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Box(Modifier.height(3.dp).fillMaxWidth().background(if (selected) Palette.Lime else Color.Transparent))
    }
}
