package com.myvitory.app.domain

/** Uma série feita: carga e repetições. */
data class DoneSet(val weightKg: Double, val reps: Int)

/** Sugestão de carga para hoje, com o motivo em uma linha. */
data class LoadSuggestion(val weightKg: Double, val increaseKg: Double)

/**
 * Progressão dupla simplificada: fez todas as séries planejadas da última vez, com a mesma carga,
 * batendo as repetições alvo, então sobe um degrau. Senão, repete a carga. Peso corporal não sobe carga.
 */
object Progression {
    fun stepFor(equipment: String): Double = when (equipment) {
        "Halter", "Halteres" -> 2.0
        "Peso corporal" -> 0.0
        else -> 2.5
    }

    fun suggest(last: List<DoneSet>, targetReps: Int, targetSets: Int, equipment: String): LoadSuggestion? {
        if (last.isEmpty() || targetReps <= 0 || last.size < targetSets) return null
        val top = last.maxOf { it.weightKg }
        if (top <= 0.0) return null
        val step = stepFor(equipment)
        if (step <= 0.0) return null
        val allAtTop = last.all { it.weightKg == top }
        val hitAll = last.all { it.reps >= targetReps }
        return if (allAtTop && hitAll) LoadSuggestion(top + step, step) else null
    }
}
