package com.myvitory.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.MyVitoryApp
import com.myvitory.app.R
import com.myvitory.app.data.update.AppUpdater
import com.myvitory.app.data.update.UpdateInfo
import com.myvitory.app.data.update.UpdateState
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun rememberUpdater(): AppUpdater? = (LocalContext.current.applicationContext as MyVitoryApp).container.updater

/** Cartão da tela Hoje: só aparece quando há versão nova (ou uma em andamento). */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val updater = rememberUpdater() ?: return
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Voltou das Configurações depois de liberar a instalação: segue sozinho.
    LifecycleResumeEffect(state) {
        val s = state
        if (s is UpdateState.NeedsPermission && updater.canInstall()) scope.launch { updater.downloadAndInstall(s.info) }
        onPauseOrDispose { }
    }

    val info: UpdateInfo
    val text: String
    var action: String? = null
    var onAction: () -> Unit = {}
    var progress: Float? = null
    when (val s = state) {
        is UpdateState.Available -> {
            info = s.info
            text = "Versão ${info.versionName} disponível" + info.notes.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            action = "Atualizar"
            onAction = { scope.launch { updater.downloadAndInstall(info) } }
        }
        is UpdateState.NeedsPermission -> {
            info = s.info
            text = "Para atualizar, permita que o MyVitory instale apps (só desta vez) e volte para cá."
            action = "Permitir"
            onAction = { context.startActivity(updater.permissionIntent()) }
        }
        is UpdateState.Downloading -> {
            info = s.info
            text = "Baixando a versão ${info.versionName}… ${(s.progress * 100).toInt()}%"
            progress = s.progress
        }
        is UpdateState.Installing -> {
            info = s.info
            text = "Instalando a versão ${info.versionName}. O app fecha e reabre atualizado."
        }
        is UpdateState.Failed -> {
            info = s.info ?: return
            text = s.message
            action = "Tentar de novo"
            onAction = { scope.launch { updater.downloadAndInstall(info) } }
        }
        else -> return
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(R.drawable.ic_refresh, null, tint = Palette.Volt, size = 20.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            action?.let {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(it, color = Palette.Volt, fontWeight = FontWeight.Bold)
                }
            }
        }
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                color = Palette.Volt,
                trackColor = Palette.SurfaceHigh,
                modifier = Modifier.fillMaxWidth().padding(end = 10.dp, bottom = 6.dp),
            )
        }
    }
}

/** Linha dos Ajustes: mostra a situação e procura na hora. */
@Composable
fun UpdateSettingsRow() {
    val updater = rememberUpdater() ?: return
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val status = when (val s = state) {
        UpdateState.Idle -> "Procura sozinha uma vez por dia."
        UpdateState.Checking -> "Procurando…"
        UpdateState.UpToDate -> "Você está na versão mais nova."
        is UpdateState.Available -> "Versão ${s.info.versionName} disponível. Atualize pela tela Hoje."
        is UpdateState.NeedsPermission -> "Falta permitir a instalação (tela Hoje)."
        is UpdateState.Downloading -> "Baixando… ${(s.progress * 100).toInt()}%"
        is UpdateState.Installing -> "Instalando…"
        is UpdateState.Failed -> s.message
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Atualizações", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        TextButton(
            onClick = { scope.launch { updater.check() } },
            enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading && state !is UpdateState.Installing,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Procurar", color = Palette.Volt, fontWeight = FontWeight.Bold) }
    }
}
