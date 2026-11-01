package com.myvitory.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.domain.Muscle
import com.myvitory.app.ui.components.SectionLabel
import com.myvitory.app.ui.theme.Palette

/** O que o editor devolve ao salvar. */
data class ExerciseDraft(val name: String, val equipment: String, val primary: List<Muscle>, val secondary: List<Muscle>)

val EQUIPMENTS = listOf("Máquina", "Polia", "Barra", "Halteres", "Halter", "Smith", "Peso corporal", "Elástico", "Outro")

/** Criar ou editar um exercício próprio: nome, equipamento e músculos que contam para a recuperação. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorDialog(
    initial: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSave: (ExerciseDraft) -> Unit,
    initialName: String = "",
) {
    var name by remember { mutableStateOf(initial?.name ?: initialName) }
    var equipment by remember { mutableStateOf(initial?.equipment ?: EQUIPMENTS.first()) }
    var primary by remember { mutableStateOf(initial?.primary?.toSet().orEmpty()) }
    var secondary by remember { mutableStateOf(initial?.secondary?.toSet().orEmpty()) }
    val valid = name.isNotBlank() && primary.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Ground,
        title = { Text(if (initial == null) "Novo exercício" else "Editar exercício") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nome") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Lime, cursorColor = Palette.Lime, focusedLabelColor = Palette.Lime),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("Equipamento")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EQUIPMENTS.forEach { e -> Choice(e, equipment == e) { equipment = e } }
                }
                SectionLabel("Músculo principal")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscle.entries.forEach { m ->
                        Choice(m.label, m in primary) {
                            primary = if (m in primary) primary - m else primary + m
                            secondary = secondary - m
                        }
                    }
                }
                SectionLabel("Também trabalha (opcional)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscle.entries.filter { it !in primary }.forEach { m ->
                        Choice(m.label, m in secondary) { secondary = if (m in secondary) secondary - m else secondary + m }
                    }
                }
                Text(
                    "Os músculos entram no painel de recuperação e no recálculo da semana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(ExerciseDraft(name.trim(), equipment, Muscle.entries.filter { it in primary }, Muscle.entries.filter { it in secondary }))
                },
            ) { Text("Salvar", color = if (valid) Palette.Lime else Palette.TextMuted, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = Palette.TextMuted) } },
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Palette.Lime else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Palette.LineStrong),
        modifier = Modifier.heightIn(min = 36.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 12.dp), Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) Palette.Ground else Palette.Text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
