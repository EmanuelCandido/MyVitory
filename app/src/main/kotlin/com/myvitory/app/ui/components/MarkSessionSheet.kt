package com.myvitory.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.myvitory.app.R
import com.myvitory.app.ui.theme.Palette

/**
 * "Como foi o treino?" — três respostas grandes, um toque cada.
 * "Fiz uma parte" abre o treino para marcar só as séries feitas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkSessionSheet(
    eyebrow: String,
    title: String,
    onAllDone: () -> Unit,
    onPartial: () -> Unit,
    onMissed: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Orange)
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                "Com essa resposta o app sabe quais músculos você treinou.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            MarkOption("Sim, treinei tudo", null, onAllDone) {
                Box(Modifier.size(34.dp).background(Palette.Lime, CircleShape), Alignment.Center) {
                    AppIcon(R.drawable.ic_check, null, tint = Palette.Ground, size = 18.dp)
                }
            }
            MarkOption("Fiz uma parte", "Você marca quais séries fez", onPartial) {
                Box(Modifier.size(34.dp).border(2.dp, Palette.Lime, CircleShape).padding(5.dp).clip(CircleShape)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(Palette.Lime))
                }
            }
            MarkOption("Não treinei", null, onMissed) {
                Box(Modifier.size(34.dp).border(2.dp, Palette.Orange, CircleShape), Alignment.Center) {
                    AppIcon(R.drawable.ic_close, null, tint = Palette.Orange, size = 16.dp)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Responder depois", color = Palette.TextMuted, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun MarkOption(title: String, subtitle: String?, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Palette.SurfaceHigh,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f))
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}
