package com.myvitory.app.domain.sync

/**
 * Junta duas versões do plano (a do celular e a da nuvem) sem perder o que cada lado mudou.
 *
 * Regra única, igual no app e no painel (web/src/sync/merge.ts), para os dois chegarem ao mesmo resultado:
 * cada treino, exercício próprio e dia da semana fica com a versão de [SnapshotTemplate.updatedAt] mais recente.
 * Empate fica com [remote]. Um apagamento vale se for mais novo que a última mudança daquela parte; uma mudança
 * feita depois do apagamento (no outro lado, sem saber dele) traz a parte de volta.
 */
object PlanMerge {
    /** Apagamentos mais velhos que isso já chegaram aos dois lados e saem da lista. */
    const val TOMBSTONE_TTL_MS = 90L * 24 * 60 * 60 * 1000

    fun merge(local: PlanSnapshot, remote: PlanSnapshot, now: Long): PlanSnapshot {
        val deleted = mutableMapOf<String, Long>()
        (local.deleted.keys + remote.deleted.keys).forEach { key ->
            deleted[key] = listOfNotNull(local.deleted[key], remote.deleted[key]).max()
        }

        val templates = pick(local.templates, remote.templates, "template:", deleted) { it.updatedAt }
        val exercises = pick(local.exercises, remote.exercises, "exercise:", deleted) { it.updatedAt }

        val week = (local.week.keys + remote.week.keys).sorted().associateWith { day ->
            val chosen = newer(local.week[day], remote.week[day]) { it.updatedAt }!!
            // Dia que aponta para um treino apagado vira descanso (sem mudar a hora: os dois lados fazem o mesmo).
            if (chosen.templateId != null && chosen.templateId !in templates) chosen.copy(templateId = null) else chosen
        }

        return PlanSnapshot(
            templates = templates,
            week = week,
            exercises = exercises,
            deleted = deleted.filterValues { now - it < TOMBSTONE_TTL_MS }.toSortedMap(),
        )
    }

    private fun <T> pick(
        local: Map<String, T>,
        remote: Map<String, T>,
        prefix: String,
        deleted: MutableMap<String, Long>,
        updatedAt: (T) -> Long,
    ): Map<String, T> {
        val result = sortedMapOf<String, T>()
        (local.keys + remote.keys).forEach { id ->
            val chosen = newer(local[id], remote[id], updatedAt)!!
            val deletedAt = deleted[prefix + id]
            when {
                deletedAt == null -> result[id] = chosen
                // Mudou depois de apagado: volta, e o apagamento deixa de valer.
                updatedAt(chosen) > deletedAt -> {
                    result[id] = chosen
                    deleted.remove(prefix + id)
                }
            }
        }
        return result
    }

    private fun <T> newer(local: T?, remote: T?, updatedAt: (T) -> Long): T? = when {
        local == null -> remote
        remote == null -> local
        updatedAt(local) > updatedAt(remote) -> local
        else -> remote
    }
}
