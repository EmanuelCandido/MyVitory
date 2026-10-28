package com.myvitory.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.BuildConfig
import com.myvitory.app.R
import com.myvitory.app.data.settings.AppSettings
import com.myvitory.app.domain.ReminderLogic
import com.myvitory.app.notify.Notifications
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settings, it.videos.searchEnabled) }
    val state by vm.settings.collectAsStateWithLifecycle(AppSettings())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permissão e bloqueio do canal são relidos sempre que a tela volta (a pessoa pode ter mudado no Android).
    var notificationsOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = Notifications.canPost(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }

    // O que ligar depois que a permissão for concedida.
    var pendingEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        if (granted) pendingEnable?.invoke()
        pendingEnable = null
    }

    /** Pede a permissão de notificação só quando a pessoa liga um lembrete (Android 13+). */
    fun enableWithPermission(enable: () -> Unit) {
        if (Notifications.canPost(context)) {
            enable()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingEnable = enable
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var pickingTime by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Ground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_arrow_back, "Voltar") }
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Section("Lembretes")
            if (!notificationsOn && (state.todayReminder || state.yesterdayQuestion)) {
                Surface(shape = RoundedCornerShape(16.dp), color = Palette.OrangeSoft, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notificações bloqueadas no Android. Os lembretes não vão aparecer.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Abrir", color = Palette.Orange, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Card {
                SwitchRow(
                    title = "Treino do dia",
                    subtitle = "Só em dia de treino, se você ainda não começou.",
                    checked = state.todayReminder,
                    onChange = { on -> if (on) enableWithPermission { scope.launch { vm.setTodayReminder(true) } } else scope.launch { vm.setTodayReminder(false) } },
                )
                HorizontalDivider(color = Palette.SurfaceHigh)
                ValueRow(
                    title = "Horário",
                    value = ReminderLogic.formatTime(state.todayReminderMinutes),
                    enabled = state.todayReminder,
                    onClick = { pickingTime = true },
                )
                HorizontalDivider(color = Palette.SurfaceHigh)
                SwitchRow(
                    title = "Perguntar se treinei ontem",
                    subtitle = "Às 9h, quando o dia ficou sem registro. Dá para responder pela notificação.",
                    checked = state.yesterdayQuestion,
                    onChange = { on -> if (on) enableWithPermission { scope.launch { vm.setYesterdayQuestion(true) } } else scope.launch { vm.setYesterdayQuestion(false) } },
                )
            }

            Section("Treino")
            Card {
                InfoRow("Descanso na tela bloqueada", "A contagem aparece na notificação, com +15 s e Pular, e o celular vibra no fim.")
            }

            Section("Vídeos")
            Card {
                InfoRow(
                    "Busca de vídeos",
                    if (vm.searchEnabled) "Ligada. Cada busca fica guardada por 7 dias." else "Desligada: o app foi gerado sem chave da YouTube API. Dá para fixar vídeos colando o link.",
                )
            }

            Section("Sobre")
            Card {
                InfoRow("MyVitory ${BuildConfig.VERSION_NAME}", "Seus treinos ficam só neste celular (e no backup do Google, se estiver ligado). Buscas e vídeos passam pelo YouTube.")
            }
        }
    }

    if (pickingTime) {
        val time = rememberTimePickerState(state.todayReminderMinutes / 60, state.todayReminderMinutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = Palette.Surface,
            title = { Text("Horário do lembrete") },
            text = {
                TimePicker(
                    state = time,
                    colors = TimePickerDefaults.colors(
                        selectorColor = Palette.Lime,
                        timeSelectorSelectedContainerColor = Palette.Lime,
                        timeSelectorSelectedContentColor = Palette.Ground,
                        clockDialColor = Palette.SurfaceHigh,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pickingTime = false
                    scope.launch { vm.setTime(time.hour * 60 + time.minute) }
                }) { Text("Salvar", color = Palette.Lime, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancelar", color = Palette.TextMuted) } },
        )
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

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // A linha inteira é o interruptor: alvo grande e um único anúncio no TalkBack.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
        }
        Switch(
            checked = checked,
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

@Composable
private fun ValueRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Palette.Surface, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Text else Palette.TextFaint, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Lime else Palette.TextFaint)
        }
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}
