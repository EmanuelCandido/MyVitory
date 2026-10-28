package com.myvitory.app.ui.exercise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.myvitory.app.R
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.video.VideoError
import com.myvitory.app.domain.VideoLinks
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.PillTabs
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.theme.BarlowCondensed
import com.myvitory.app.ui.theme.Palette
import com.myvitory.app.ui.video.YouTubePlayerBox
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseScreen(
    exerciseId: String,
    sessionId: Long?,
    onBack: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val vm = appViewModel(key = "exercise-$exerciseId-$sessionId") { ExerciseViewModel(it.training, it.videos, exerciseId, sessionId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    var pasting by remember { mutableStateOf(false) }
    var playerMessage by remember(state.current?.videoId) { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar") }
            Text(state.exercise?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            val current = state.current
            if (current != null) {
                YouTubePlayerBox(
                    videoId = current.videoId,
                    onError = { error ->
                        val canSkip = state.videos.size > 1 && error != PlayerConstants.PlayerError.HTML_5_PLAYER
                        val then = if (canSkip) " Pulamos para o próximo." else ""
                        playerMessage = when (error) {
                            PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> "Esse vídeo só toca no app do YouTube.$then"
                            PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> "Vídeo removido ou privado.$then"
                            else -> "Não deu para tocar. Confira a internet ou escolha outro vídeo."
                        }
                        if (canSkip) vm.nextVideo()
                    },
                )
            } else {
                PlayerPlaceholder(state.searching, state.searchEnabled, state.error, onPaste = { pasting = true })
            }

            if (current != null) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(current.channel.takeIf { it.isNotBlank() }, "vídeo ${state.selected + 1} de ${state.videos.size}").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.togglePin(current) }, modifier = Modifier.size(48.dp)) {
                        AppIcon(
                            if (current.pinned) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                            if (current.pinned) "Desafixar vídeo" else "Fixar este vídeo para o exercício",
                            tint = if (current.pinned) Palette.Lime else Palette.TextMuted,
                            size = 20.dp,
                        )
                    }
                    if (state.videos.size > 1) {
                        TextButton(onClick = vm::nextVideo, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Outro vídeo", color = Palette.Lime, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                playerMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Orange, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }

            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                state.exercise?.let { exercise ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        exercise.primary.forEach { MuscleChip(it.label, primary = true) }
                        exercise.secondary.forEach { MuscleChip(it.label, primary = false) }
                        MuscleChip(exercise.equipment, primary = false, muted = true)
                    }
                }

                if (state.tips.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Pontos-chave", style = MaterialTheme.typography.headlineSmall)
                        state.tips.forEachIndexed { i, tip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${i + 1}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = BarlowCondensed), color = Palette.Lime, modifier = Modifier.width(18.dp))
                                Text(tip, style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.88f))
                            }
                        }
                    }
                }

                if (state.alternatives.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(if (state.canSwap) "Trocar por" else "Parecidos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            Text("mesmo músculo principal", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                        }
                        if (state.canSwap) {
                            PillTabs(listOf("Só neste treino", "No plano todo"), if (state.swapInPlan) 1 else 0, { vm.setSwapInPlan(it == 1) })
                        }
                        state.alternatives.forEach { alt ->
                            AlternativeRow(
                                exercise = alt,
                                canSwap = state.canSwap,
                                onUse = {
                                    vm.swap(alt) { revert ->
                                        undo.show(
                                            if (state.swapInPlan) "Trocado por ${alt.name} no plano" else "Trocado por ${alt.name} neste treino",
                                            revert,
                                        )
                                        onBack()
                                    }
                                },
                                onOpen = { onOpenExercise(alt.id) },
                            )
                        }
                    }
                }

                MoreVideos(state, onSelect = vm::select, onRefresh = { vm.loadVideos(forceRefresh = true) }, onPaste = { pasting = true })
            }
        }
    }

    if (pasting) {
        PasteLinkDialog(onDismiss = { pasting = false }, onSubmit = { text -> vm.pinLink(text).also { if (it) pasting = false } })
    }
}

@Composable
private fun PlayerPlaceholder(searching: Boolean, searchEnabled: Boolean, error: VideoError?, onPaste: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.Surface), Alignment.Center) {
        if (searching) {
            CircularProgressIndicator(color = Palette.Lime)
        } else {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when {
                        !searchEnabled -> "Busca de vídeos desligada: falta a chave da YouTube API. Você pode colar o link de um vídeo."
                        error == VideoError.Offline -> "Sem internet. Os vídeos voltam quando a conexão voltar."
                        error == VideoError.Quota -> "Limite diário de buscas atingido. Tente amanhã ou cole um link."
                        else -> "Nenhum vídeo encontrado. Cole o link de um vídeo do YouTube."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted,
                )
                OutlinedButton(onClick = onPaste, shape = CircleShape, border = BorderStroke(1.dp, Palette.LineStrong)) {
                    AppIcon(R.drawable.ic_link, null, tint = Palette.Text, size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Colar link", color = Palette.Text)
                }
            }
        }
    }
}

@Composable
private fun MuscleChip(text: String, primary: Boolean, muted: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            primary -> Palette.Lime
            muted -> Palette.TextMuted
            else -> Palette.Text
        },
        modifier = Modifier
            .clip(CircleShape)
            .then(if (primary) Modifier.border(1.5.dp, Palette.Lime, CircleShape) else Modifier.background(Palette.SurfaceHigh))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun AlternativeRow(exercise: ExerciseEntity, canSwap: Boolean, onUse: () -> Unit, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(16.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    (listOf(exercise.equipment) + exercise.secondary.map { it.label }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
            if (canSwap) {
                OutlinedButton(onClick = onUse, shape = CircleShape, border = BorderStroke(1.dp, Palette.Lime), modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Usar", color = Palette.Lime, fontWeight = FontWeight.Bold)
                }
            } else {
                AppIcon(R.drawable.ic_chevron_right, null, tint = Palette.Neutral, size = 18.dp)
            }
        }
    }
}

@Composable
private fun MoreVideos(state: ExerciseUiState, onSelect: (Int) -> Unit, onRefresh: () -> Unit, onPaste: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vídeos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (state.searchEnabled) {
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    AppIcon(R.drawable.ic_refresh, "Buscar vídeos de novo", tint = Palette.TextMuted, size = 20.dp)
                }
            }
            IconButton(onClick = onPaste, modifier = Modifier.size(48.dp)) {
                AppIcon(R.drawable.ic_link, "Colar link de vídeo", tint = Palette.TextMuted, size = 20.dp)
            }
        }
        state.videos.forEachIndexed { index, video ->
            val selected = index == state.selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Palette.Surface else Palette.Ground)
                    .clickable { onSelect(index) }
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh)) {
                    AsyncImage(model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    video.durationSec?.let {
                        Text(
                            VideoLinks.formatDuration(it),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(4.dp)).background(Palette.Ground.copy(alpha = 0.8f)).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (video.pinned) SectionLabel("Fixado")
                    Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (selected) Palette.Lime else Palette.Text)
                    if (video.channel.isNotBlank()) Text(video.channel, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
                }
            }
        }
        if (state.videos.isEmpty() && !state.searching) {
            Text("Nenhum vídeo ainda.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }
    }
}

@Composable
private fun PasteLinkDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("Fixar um vídeo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cole o link do YouTube. Ele passa a ser o primeiro vídeo deste exercício.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; invalid = false },
                    placeholder = { Text("youtube.com/watch?v=…") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = { if (invalid) Text("Não parece um link do YouTube.") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Lime, cursorColor = Palette.Lime),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { invalid = !onSubmit(text) }) { Text("Fixar", color = Palette.Lime, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.TextMuted) } },
    )
}
