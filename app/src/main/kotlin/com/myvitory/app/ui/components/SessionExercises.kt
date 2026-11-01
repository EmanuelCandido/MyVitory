package com.myvitory.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import coil3.compose.LocalPlatformContext
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.myvitory.app.R
import com.myvitory.app.data.ExerciseMedia
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.ui.theme.Palette

/**
 * Miniatura do movimento: só o primeiro quadro do GIF, sem animar. Listas com dezenas de GIFs animados
 * decodificando ao mesmo tempo travam a rolagem; a animação fica para a tela do exercício.
 */
@Composable
fun ExerciseThumb(exerciseId: String, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Palette.SurfaceHigh), Alignment.Center) {
        val gif = ExerciseMedia.gifUrl(exerciseId)
        if (gif != null) {
            val context = LocalPlatformContext.current
            val request = remember(gif) {
                ImageRequest.Builder(context)
                    .data(gif)
                    .decoderFactory(BitmapFactoryDecoder.Factory())
                    // Chave própria: o quadro parado não pode ocupar o lugar do GIF animado no cache de memória.
                    .memoryCacheKey("$gif#thumb")
                    .build()
            }
            // Fundo branco: os GIFs do ExerciseDB são desenhados sobre branco.
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color.White))
        } else {
            AppIcon(R.drawable.ic_dumbbell, null, tint = Palette.TextMuted, size = size * 0.42f)
        }
    }
}

/** Exercícios de um treino: cada linha abre o exercício (GIF, vídeo e pontos-chave). */
@Composable
fun SessionExerciseList(items: List<TemplateItemRow>, onOpenExercise: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = Palette.Line)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { onOpenExercise(item.exerciseId) }
                    .semantics(mergeDescendants = true) { contentDescription = "${item.exerciseName}, ${item.sets} séries de ${item.reps}. Ver exercício" }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseThumb(item.exerciseId, 40.dp)
                Text(item.exerciseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${item.sets} × ${item.reps}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                AppIcon(R.drawable.ic_chevron_right, null, tint = Palette.Neutral, size = 16.dp)
            }
        }
    }
}
