package com.myvitory.app.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.R
import com.myvitory.app.data.WorkoutExercise
import com.myvitory.app.data.db.SetLogEntity
import com.myvitory.app.data.formatKg
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.ui.LocalUndo
import com.myvitory.app.ui.appViewModel
import com.myvitory.app.ui.components.AppIcon
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.components.formatClock
import com.myvitory.app.ui.theme.BarlowCondensed
import com.myvitory.app.ui.theme.Palette
import com.myvitory.app.timer.RestTimer
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(sessionId: Long, onClose: () -> Unit, onOpenExercise: (String) -> Unit) {
    val vm = appViewModel(key = "workout-$sessionId") { WorkoutViewModel(it.training, it.restTimer, sessionId) }
    val workout by vm.workout.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    var askEmpty by remember { mutableStateOf(false) }

    // Tela acesa durante o treino.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val data = workout
    val currentIndex = data?.exercises?.indexOfFirst { ex -> ex.sets.any { !it.done } }?.takeIf { it >= 0 }
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    // Ao terminar um exercício, o próximo abre sozinho.
    LaunchedEffect(currentIndex) { expanded = null }
    val open = expanded ?: currentIndex

    Box(Modifier.fillMaxSize().background(Palette.Ground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                name = data?.session?.name.orEmpty(),
                startedAt = data?.session?.startedAt,
                finishedAt = data?.session?.finishedAt,
                onClose = onClose,
                onFinish = {
                    vm.finish { status ->
                        when (status) {
                            null -> askEmpty = true
                            SessionStatus.DONE -> { undo.show("Treino concluído", null); onClose() }
                            else -> { undo.show("Treino salvo como parcial", null); onClose() }
                        }
                    }
                },
            )
            if (data != null && data.exercises.isNotEmpty()) {
                val total = data.exercises.size
                val position = (currentIndex ?: total - 1) + 1
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (currentIndex == null) "Todas as séries marcadas" else "Exercício $position de $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        data.exercises.forEachIndexed { i, ex ->
                            val done = ex.sets.all { it.done }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            done -> Palette.Lime
                                            i == currentIndex -> Palette.Lime.copy(alpha = 0.5f)
                                            else -> Palette.Line
                                        }
                                    )
                            )
                        }
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (rest != null) 140.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val exercises = data?.exercises.orEmpty()
                items(exercises.size, key = { "${exercises[it].position}-${exercises[it].exerciseId}" }) { index ->
                    val ex = exercises[index]
                    if (index == open) {
                        ExerciseCard(
                            exercise = ex,
                            onToggle = { log -> vm.toggleSet(log, ex.name) },
                            onWeight = vm::setWeight,
                            onReps = vm::setReps,
                            onAddSet = { vm.addSet(ex.exerciseId) },
                            onHowTo = { onOpenExercise(ex.exerciseId) },
                        )
                    } else {
                        ExerciseRow(index + 1, ex, onClick = { expanded = index })
                    }
                }
                if (data != null && data.exercises.isEmpty()) {
                    item {
                        Text(
                            "Este treino não tem exercícios. Adicione exercícios em Semana › Plano base.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = rest != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            rest?.let { timer ->
                RestBar(timer, onMinus = { vm.adjustRest(-15) }, onPlus = { vm.adjustRest(15) }, onSkip = vm::skipRest, onFinished = vm::restReachedZero)
            }
        }
    }

    if (askEmpty) {
        AlertDialog(
            onDismissRequest = { askEmpty = false },
            title = { Text("Nenhuma série marcada") },
            text = { Text("Você fez esse treino? Marque as séries ou escolha uma opção.") },
            confirmButton = {
                TextButton(onClick = {
                    askEmpty = false
                    vm.markWithoutSets(SessionStatus.DONE) { undo.show("Treino marcado como feito", null); onClose() }
                }) { Text("Fiz tudo", color = Palette.Lime) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askEmpty = false
                        vm.markWithoutSets(SessionStatus.MISSED) { undo.show("Marcado como não treinado", null); onClose() }
                    }) { Text("Não treinei", color = Palette.Orange) }
                    TextButton(onClick = { askEmpty = false }) { Text("Voltar", color = Palette.TextMuted) }
                }
            },
            containerColor = Palette.Surface,
        )
    }
}

@Composable
private fun TopBar(name: String, startedAt: Long?, finishedAt: Long?, onClose: () -> Unit, onFinish: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt, finishedAt) {
        while (startedAt != null && finishedAt == null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            AppIcon(R.drawable.ic_close, "Sair do treino")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            if (startedAt != null) {
                val end = finishedAt ?: now
                Text(
                    formatClock((end - startedAt) / 1000),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold),
                )
            }
        }
        TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Finalizar", color = Palette.Lime, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ExerciseRow(number: Int, exercise: WorkoutExercise, onClick: () -> Unit) {
    val done = exercise.sets.count { it.done }
    val complete = done == exercise.sets.size && done > 0
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (complete) Palette.Ground else Palette.Surface)
            .then(if (complete) Modifier.border(1.dp, Palette.Surface, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (complete) Palette.Lime else Palette.SurfaceHigh),
            Alignment.Center,
        ) {
            if (complete) AppIcon(R.drawable.ic_check, null, tint = Palette.Ground, size = 14.dp)
            else Text("$number", style = MaterialTheme.typography.labelMedium, color = Palette.TextMuted)
        }
        Text(exercise.name, style = MaterialTheme.typography.bodyMedium, color = if (complete) Palette.TextMuted else Palette.Text, modifier = Modifier.weight(1f))
        Text("$done/${exercise.sets.size}", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
    }
}

@Composable
private fun ExerciseCard(
    exercise: WorkoutExercise,
    onToggle: (SetLogEntity) -> Unit,
    onWeight: (SetLogEntity, Double) -> Unit,
    onReps: (SetLogEntity, Int) -> Unit,
    onAddSet: () -> Unit,
    onHowTo: () -> Unit,
) {
    val firstOpen = exercise.sets.firstOrNull { !it.done }?.id
    val hasOpenSets = firstOpen != null
    Surface(shape = RoundedCornerShape(22.dp), color = Palette.Surface, border = BorderStroke(1.dp, Palette.SurfaceHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.93f))
                Text(
                    exercise.lastTime?.let { "Última vez: $it" } ?: "Primeira vez: ajuste a carga",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
                exercise.suggestion?.let { s ->
                    Text(
                        "↑ +${formatKg(s.increaseKg)} kg hoje: você bateu todas as repetições da última vez",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Lime,
                    )
                }
            }
            // A tela do exercício tem o vídeo e, quando há séries abertas, a lista "Trocar por".
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(R.drawable.ic_play, "Como fazer", onHowTo)
                if (hasOpenSets) ActionPill(R.drawable.ic_swap, "Trocar", onHowTo)
            }
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Série", Modifier.width(40.dp))
                SectionLabel("Kg", Modifier.weight(1f))
                SectionLabel("Reps", Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            exercise.sets.forEach { log ->
                SetRow(log, active = log.id == firstOpen, onToggle = onToggle, onWeight = onWeight, onReps = onReps)
            }
            TextButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                AppIcon(R.drawable.ic_plus, null, tint = Palette.TextMuted, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Adicionar série", color = Palette.TextMuted, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun SetRow(
    log: SetLogEntity,
    active: Boolean,
    onToggle: (SetLogEntity) -> Unit,
    onWeight: (SetLogEntity, Double) -> Unit,
    onReps: (SetLogEntity, Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Palette.SurfaceHigh else Palette.Surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${log.setIndex + 1}",
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
        )
        NumberField(
            value = formatKg(log.weightKg),
            label = "Carga da série ${log.setIndex + 1}",
            keyboard = KeyboardType.Decimal,
            onValue = { text -> text.replace(',', '.').toDoubleOrNull()?.let { onWeight(log, it) } },
            modifier = Modifier.weight(1f),
            key = log.id,
        )
        NumberField(
            value = log.reps.toString(),
            label = "Repetições da série ${log.setIndex + 1}",
            keyboard = KeyboardType.Number,
            onValue = { text -> text.toIntOrNull()?.let { onReps(log, it) } },
            modifier = Modifier.weight(1f),
            key = log.id,
        )
        // Caixa de seleção de verdade: o TalkBack lê "Série 2, 150 kg, 10 repetições, marcada".
        val description = "Série ${log.setIndex + 1}, ${formatKg(log.weightKg)} kg, ${log.reps} repetições"
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (log.done) Modifier.background(Palette.Lime)
                    else Modifier.border(2.dp, Palette.Neutral, RoundedCornerShape(14.dp))
                )
                .toggleable(value = log.done, role = Role.Checkbox, onValueChange = { onToggle(log) })
                .semantics { contentDescription = description },
            Alignment.Center,
        ) {
            AppIcon(R.drawable.ic_check, null, tint = if (log.done) Palette.Ground else Palette.Neutral, size = 22.dp)
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    keyboard: KeyboardType,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    key: Long,
) {
    // O texto local manda enquanto a pessoa digita; o banco só recebe valores válidos.
    var text by remember(key) { mutableStateOf(value) }
    BasicTextField(
        value = text,
        onValueChange = { new ->
            if (new.length <= 6) {
                text = new
                onValue(new)
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            fontSize = MaterialTheme.typography.titleLarge.fontSize,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Text,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(Palette.Lime),
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Ground)
            .border(1.dp, Palette.LineStrong, RoundedCornerShape(10.dp))
            .semantics { contentDescription = label },
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), Alignment.Center) { inner() } },
    )
}

@Composable
private fun RestBar(timer: RestTimer, onMinus: () -> Unit, onPlus: () -> Unit, onSkip: () -> Unit, onFinished: (RestTimer) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= timer.endsAt) {
                // Tela e serviço disputam o fim; só quem encerrar primeiro vibra.
                onFinished(timer)
                break
            }
            delay(250)
        }
    }
    val remaining = ((timer.endsAt - now + 999) / 1000).coerceAtLeast(0)
    val progress = 1f - remaining.toFloat() / timer.totalSec.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Palette.Surface).navigationBarsPadding()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = Palette.Blue,
            trackColor = Palette.Line,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("DESCANSO", style = MaterialTheme.typography.labelSmall, color = Palette.Blue)
                Text(formatClock(remaining), style = MaterialTheme.typography.displaySmall.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * 0.85f))
            }
            RestButton("−15", "Menos 15 segundos", onMinus)
            RestButton("+15", "Mais 15 segundos", onPlus)
            Surface(onClick = onSkip, shape = CircleShape, color = Palette.Text, modifier = Modifier.heightIn(min = 44.dp)) {
                Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) {
                    Text("Pular", color = Palette.Ground, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun RestButton(text: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = description },
        shape = CircleShape,
        border = BorderStroke(1.dp, Palette.LineStrong),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(text, color = Palette.Text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ActionPill(icon: Int, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Palette.SurfaceHigh, modifier = Modifier.heightIn(min = 44.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(icon, null, tint = Palette.Text, size = 16.dp)
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
