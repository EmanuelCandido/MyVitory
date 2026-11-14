package com.myvitory.app.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myvitory.app.data.formatKg
import com.myvitory.app.domain.Weighing
import com.myvitory.app.domain.WeightGoal
import com.myvitory.app.domain.WeightLogic
import com.myvitory.app.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** "78,4" ou "78.4" viram 78.4; o resto vira null. */
internal fun parseKg(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { WeightLogic.isValidKg(it) }

/**
 * Registrar ou corrigir uma pesagem. A data começa em hoje e pode ir para qualquer dia passado.
 * [existing] é a pesagem de cada data, para avisar que salvar substitui a daquele dia.
 */
@Composable
fun WeighingDialog(
    initial: Weighing?,
    today: LocalDate,
    suggestionKg: Double?,
    existing: Map<LocalDate, Double>,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Double) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var text by remember { mutableStateOf((initial?.kg ?: suggestionKg)?.let(::formatKg).orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    val kg = parseKg(text)
    val replaces = existing[date]?.takeIf { date != initial?.date }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(if (initial == null) "Registrar peso" else "Editar pesagem") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    isError = text.isNotEmpty() && kg == null,
                    supportingText = if (text.isNotEmpty() && kg == null) {
                        { Text("Entre ${WeightLogic.MIN_KG.toInt()} e ${WeightLogic.MAX_KG.toInt()} kg") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Volt, cursorColor = Palette.Volt, focusedLabelColor = Palette.Volt),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRow("Data", date.longLabel(today), onClick = { pickingDate = true })
                if (replaces != null) {
                    Text("Já tem ${formatKg(replaces)} kg nesse dia. Salvar substitui.", style = MaterialTheme.typography.bodySmall, color = Palette.Flame)
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Apagar pesagem", color = Palette.Flame, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { kg?.let { onSave(date, it) } }, enabled = kg != null) {
                Text("Salvar", color = if (kg != null) Palette.Volt else Palette.TextFaint, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.TextMuted) } },
    )

    if (pickingDate) {
        PickDate(date, allow = { !it.isAfter(today) }, onDismiss = { pickingDate = false }, onPick = { date = it; pickingDate = false })
    }
}

/** Meta: peso alvo e, se quiser, um prazo. */
@Composable
fun GoalDialog(
    initial: WeightGoal?,
    currentKg: Double,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Double, LocalDate?) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var text by remember { mutableStateOf(initial?.targetKg?.let(::formatKg).orEmpty()) }
    var by by remember { mutableStateOf(initial?.by) }
    var pickingDate by remember { mutableStateOf(false) }
    val kg = parseKg(text)
    val valid = kg != null && kg != currentKg

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(if (initial == null) "Definir meta" else "Editar meta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Você está com ${formatKg(currentKg)} kg.", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
                    label = { Text("Peso alvo (kg)") },
                    singleLine = true,
                    isError = text.isNotEmpty() && kg == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Volt, cursorColor = Palette.Volt, focusedLabelColor = Palette.Volt),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRow("Prazo", by?.longLabel(today) ?: "Sem prazo", onClick = { pickingDate = true })
                if (by != null) {
                    TextButton(onClick = { by = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tirar prazo", color = Palette.TextMuted) }
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Apagar meta", color = Palette.Flame, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { kg?.let { onSave(it, by) } }, enabled = valid) {
                Text("Salvar", color = if (valid) Palette.Volt else Palette.TextFaint, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.TextMuted) } },
    )

    if (pickingDate) {
        PickDate(by ?: today.plusMonths(3), allow = { it.isAfter(today) }, onDismiss = { pickingDate = false }, onPick = { by = it; pickingDate = false })
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Palette.SurfaceHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Palette.Volt)
        }
    }
}

/** O DatePicker trabalha em milissegundos UTC; a conversão fica toda aqui. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDate(initial: LocalDate, allow: (LocalDate) -> Boolean, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allow(utcTimeMillis.date())
        },
    )
    val colors = DatePickerDefaults.colors(
        containerColor = Palette.Surface,
        selectedDayContainerColor = Palette.Volt,
        selectedDayContentColor = Palette.Ground,
        todayDateBorderColor = Palette.Volt,
        todayContentColor = Palette.Volt,
        selectedYearContainerColor = Palette.Volt,
        selectedYearContentColor = Palette.Ground,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.date()) } }) {
                Text("OK", color = Palette.Volt, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.TextMuted) } },
    ) {
        DatePicker(state = state, colors = colors)
    }
}
