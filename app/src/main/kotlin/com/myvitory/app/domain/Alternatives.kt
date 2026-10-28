package com.myvitory.app.domain

/** O mínimo de um exercício que a escolha de alternativas precisa. */
data class ExerciseRef(
    val id: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    val equipment: String,
)

object Alternatives {
    /**
     * Exercícios que trabalham o mesmo músculo principal, do mais parecido para o menos.
     * Equipamento diferente ganha um pouco: o motivo mais comum da troca é aparelho ocupado.
     */
    fun rank(target: ExerciseRef, catalog: List<ExerciseRef>, limit: Int = 5): List<ExerciseRef> =
        catalog
            .asSequence()
            .filter { it.id != target.id && it.primary.any { m -> m in target.primary } }
            .map { candidate -> candidate to score(target, candidate) }
            .sortedWith(compareByDescending<Pair<ExerciseRef, Double>> { it.second }.thenBy { it.first.id })
            .take(limit)
            .map { it.first }
            .toList()

    private fun score(target: ExerciseRef, candidate: ExerciseRef): Double {
        var score = 0.0
        if (candidate.primary.toSet() == target.primary.toSet()) score += 3
        score += candidate.primary.count { it in target.primary } * 2
        score += candidate.secondary.count { it in target.secondary }
        score -= candidate.secondary.count { it !in target.secondary && it !in target.primary } * 0.5
        if (candidate.equipment != target.equipment) score += 0.5
        return score
    }
}
