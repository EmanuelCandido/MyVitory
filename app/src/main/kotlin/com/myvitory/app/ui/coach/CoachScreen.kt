package com.myvitory.app.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.BuildConfig
import com.myvitory.app.R
import com.myvitory.app.data.sync.CoachLink
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.twoDigits
import com.myvitory.app.ui.theme.BarlowCondensed
import com.myvitory.app.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
fun CoachScreen(onBack: () -> Unit) {
    val vm = appViewModel { CoachViewModel(it.sync!!) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar") }
            Text("Painel do treinador", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { message ->
                Surface(shape = RoundedCornerShape(16.dp), color = Palette.OrangeSoft, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::dismissError) { Text("OK", color = Palette.Orange) }
                    }
                }
            }
            if (!state.sync.connected) {
                Disconnected(busy = state.busy, onConnect = vm::connect)
            } else {
                Connected(state, vm, onDisconnect = { confirmDisconnect = true })
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar do painel?") },
            text = {
                Text(
                    "O treinador perde o acesso e o que estava na nuvem (plano, histórico e peso) é apagado. " +
                        "Tudo continua no seu celular.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDisconnect = false; vm.disconnect() }) { Text("Desconectar e apagar", color = Palette.Orange) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } },
            containerColor = Palette.SurfaceHigh,
        )
    }
}

@Composable
private fun Disconnected(busy: Boolean, onConnect: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Treine com o plano do seu treinador", style = MaterialTheme.typography.titleMedium)
            Text(
                "Seu treinador monta e ajusta os treinos pelo navegador e as mudanças chegam aqui sozinhas. " +
                    "Ele vê os treinos que você fez, as cargas e seus comentários. O peso só vai se você ligar.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Seu nome (aparece no painel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Lime,
                    unfocusedBorderColor = Palette.LineStrong,
                    focusedLabelColor = Palette.Lime,
                    cursorColor = Palette.Lime,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onConnect(name) },
                enabled = !busy && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (busy) "Conectando..." else "Conectar", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Connected(state: CoachUiState, vm: CoachViewModel, onDisconnect: () -> Unit) {
    val sync = state.sync
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Section("Parear um treinador")
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val code = sync.pairCode?.takeIf { (sync.pairCodeExpiresAt ?: 0) > now }
            if (code != null) {
                val formatted = CoachLink.formatCode(code)
                Text(
                    formatted,
                    style = MaterialTheme.typography.displaySmall.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                    color = Palette.Lime,
                    modifier = Modifier.semantics { contentDescription = "Código ${code.toList().joinToString(" ")}" },
                )
                Text("Vale até ${clock(sync.pairCodeExpiresAt!!)}.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Text(
                "No painel (${BuildConfig.FIREBASE_PROJECT_ID}.web.app), o treinador entra com a conta Google e digita o código.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
            )
            TextButton(onClick = vm::newCode, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (code == null) "Gerar código" else "Gerar outro código", color = Palette.Lime, fontWeight = FontWeight.Bold)
            }
        }
    }

    Section("Treinadores")
    Card {
        val coaches = state.cloud?.coaches.orEmpty()
        if (coaches.isEmpty()) {
            Text(
                if (state.cloud == null) "Carregando..." else "Nenhum treinador ainda.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextMuted,
                modifier = Modifier.padding(16.dp),
            )
        }
        coaches.forEachIndexed { index, coach ->
            if (index > 0) HorizontalDivider(color = Palette.SurfaceHigh)
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(coach.name, style = MaterialTheme.typography.titleMedium)
                    if (coach.email.isNotBlank()) Text(coach.email, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
                }
                TextButton(onClick = { vm.removeCoach(coach.uid) }, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Remover", color = Palette.Orange)
                }
            }
        }
    }

    Section("Sincronização")
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                sync.lastSyncAt?.let { "Última: ${dayAndClock(it)}" } ?: "Ainda não sincronizou.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Acontece sozinha a cada 15 minutos, logo depois de você mudar algo e, com o app aberto, quando o treinador salva.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
            )
            sync.lastError?.let { Text("Último erro: $it", style = MaterialTheme.typography.bodySmall, color = Palette.Orange) }
            TextButton(onClick = vm::syncNow, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (state.busy) "Sincronizando..." else "Sincronizar agora", color = Palette.Lime, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = Palette.SurfaceHigh)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = sync.shareWeight, role = Role.Switch, enabled = !state.busy, onValueChange = vm::setShareWeight)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Compartilhar meu peso", style = MaterialTheme.typography.titleMedium)
                Text("O treinador vê suas pesagens no painel. Desligar apaga da nuvem.", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
            Switch(
                checked = sync.shareWeight,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.Lime,
                    checkedThumbColor = Palette.Ground,
                    uncheckedTrackColor = Palette.SurfaceHigh,
                    uncheckedThumbColor = Palette.TextMuted,
                    uncheckedBorderColor = Palette.LineStrong,
                ),
            )
        }
    }

    TextButton(onClick = onDisconnect, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp)) {
        Text("Desconectar e apagar da nuvem", color = Palette.Orange)
    }
}

@Composable
private fun Section(text: String) {
    SectionLabel(text, Modifier.padding(start = 4.dp, top = 16.dp).semantics { heading() })
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Palette.Surface, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

private fun clock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return "${time.hour.twoDigits()}:${time.minute.twoDigits()}"
}

private fun dayAndClock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (time.toLocalDate()) {
        today -> "hoje"
        today.minusDays(1) -> "ontem"
        else -> "${time.dayOfMonth.twoDigits()}/${time.monthValue.twoDigits()}"
    }
    return "$day às ${clock(millis)}"
}
