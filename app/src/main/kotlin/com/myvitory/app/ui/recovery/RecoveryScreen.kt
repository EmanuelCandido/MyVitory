package com.myvitory.app.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.domain.MuscleRecovery
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.short
import com.myvitory.app.ui.theme.Palette
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecoveryScreen(contentPadding: PaddingValues) {
    val vm = appViewModel { RecoveryViewModel(it.coach, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = 24.dp),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
            Text("Recuperação", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Agora, pelos seus treinos registrados", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
        }

        if (!state.loading) {
            TodayCheck(state, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        }

        if (state.recovering.isNotEmpty()) {
            SectionLabel("Ainda recuperando", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                state.recovering.forEach { m ->
                    RecoveringRow(m, state.nowMillis)
                    HorizontalDivider(color = Palette.Surface)
                }
            }
        }

        if (state.ready.isNotEmpty()) {
            SectionLabel("Prontos", Modifier.padding(start = 20.dp, top = 24.dp, bottom = 10.dp))
            FlowRow(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.ready.forEach { m ->
                    Row(
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(CircleShape)
                            .background(Palette.Surface)
                            .padding(start = 10.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AppIcon(R.drawable.ic_check, null, tint = Palette.Lime, size = 14.dp)
                        Text(m.muscle.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Text(
            (if (state.beginner) "Nível iniciante: +24 h em cada janela. " else "") +
                "Janelas: 72 h para grupos grandes, 48 h para ombros e braços, 24 h para abdômen e panturrilha. " +
                "Músculo usado só como apoio recupera na metade do tempo.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp),
        )
    }
}

@Composable
private fun TodayCheck(state: RecoveryUiState, modifier: Modifier = Modifier) {
    val today = state.today
    val notReady = state.todayNotReady
    val (title, body, color) = when {
        today == null -> Triple("Hoje é descanso", "Os músculos agradecem.", Palette.Blue)
        notReady.isEmpty() -> Triple("Treino de hoje liberado", "${today.session.name}: músculos principais prontos.", Palette.Lime)
        else -> Triple(
            "Atenção no treino de hoje",
            notReady.joinToString(", ") { "${it.muscle.label} em ${it.percent}%" } + ". Considere menos carga nesses exercícios.",
            Palette.Orange,
        )
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Palette.Surface, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color), Alignment.Center) {
                AppIcon(if (color == Palette.Orange) R.drawable.ic_timer else R.drawable.ic_check, null, tint = Palette.Ground, size = 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun RecoveringRow(m: MuscleRecovery, nowMillis: Long) {
    val zone = ZoneId.systemDefault()
    val last = m.lastAtMillis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).dayOfWeek.short().lowercase() }
    val ready = m.readyAtMillis?.let { readyLabel(it, nowMillis, zone) }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(m.muscle.label, style = MaterialTheme.typography.titleMedium)
            if (last != null) Text("  · $last", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.weight(1f))
            if (ready != null) Text("pronto $ready", style = MaterialTheme.typography.titleSmall, color = Palette.Blue)
        }
        LinearProgressIndicator(
            progress = { m.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .semantics { contentDescription = "${m.muscle.label} ${m.percent}% recuperado" },
            color = Palette.Blue,
            trackColor = Palette.SurfaceHigh,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

private fun readyLabel(readyAt: Long, now: Long, zone: ZoneId): String {
    val ready = LocalDateTime.ofInstant(Instant.ofEpochMilli(readyAt), zone)
    val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val day = when (ready.toLocalDate()) {
        today -> "hoje"
        today.plusDays(1) -> "amanhã"
        else -> ready.dayOfWeek.short().lowercase()
    }
    return "$day, ${ready.hour}h"
}
