package com.myvitory.app.domain.sync

/**
 * O plano de treino como o painel do treinador vê: o que vai e volta entre o celular e a nuvem.
 * Ids são os estáveis (remoteId dos treinos, id dos exercícios), nunca os ids locais do Room.
 * Cada parte tem o horário da última mudança (ms) e, quando os dois lados mudaram, a mais recente vence.
 */
data class PlanSnapshot(
    /** remoteId → treino. */
    val templates: Map<String, SnapshotTemplate> = emptyMap(),
    /** 1 = segunda ... 7 = domingo. Dia ausente = nunca mexido (descanso). */
    val week: Map<Int, SnapshotDay> = emptyMap(),
    /** Exercícios próprios (os do catálogo já existem nos dois lados). id → exercício. */
    val exercises: Map<String, SnapshotExercise> = emptyMap(),
    /** "template:<id>" ou "exercise:<id>" → quando foi apagado. Impede que o outro lado traga de volta. */
    val deleted: Map<String, Long> = emptyMap(),
    /** Datas com algo próprio ("2026-11-02" → feriado ou treino escolhido para o dia). */
    val dates: Map<String, SnapshotDate> = emptyMap(),
) {
    companion object {
        /** Versão do formato no Firestore. Mudar quebra quem ainda está na anterior. 2: academia fechada e datas. */
        const val SCHEMA = 2
    }
}

data class SnapshotTemplate(
    val code: String,
    val name: String,
    val updatedAt: Long,
    /** Na ordem do treino. */
    val items: List<SnapshotItem>,
)

data class SnapshotItem(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
    val note: String = "",
)

/** [closed]: a academia não abre neste dia da semana; o recálculo nunca põe treino nele. */
data class SnapshotDay(val templateId: String?, val updatedAt: Long, val closed: Boolean = false)

/**
 * O que vale numa data. [closed]: academia fechada (feriado), com [label] opcional ("Finados").
 * [override]: o dia segue [templateId] (null = descanso) em vez do plano base. Sem os dois, a data segue o plano
 * (a linha fica para o "mais recente vence" saber que alguém desfez a mudança).
 */
data class SnapshotDate(
    val closed: Boolean,
    val label: String,
    val override: Boolean,
    val templateId: String?,
    val updatedAt: Long,
)

data class SnapshotExercise(
    val name: String,
    val equipment: String,
    /** Nomes do enum Muscle. */
    val primary: List<String>,
    val secondary: List<String>,
    val updatedAt: Long,
)
