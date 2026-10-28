package com.myvitory.app.domain

/**
 * Janela de recuperação padrão por grupo, em horas (prancheta "Motor de recálculo").
 */
enum class Muscle(val label: String, val recoveryHours: Int) {
    CHEST("Peito", 72),
    BACK("Costas", 72),
    QUADS("Quadríceps", 72),
    HAMSTRINGS("Posterior", 72),
    GLUTES("Glúteos", 72),
    SHOULDERS("Ombros", 48),
    BICEPS("Bíceps", 48),
    TRICEPS("Tríceps", 48),
    CALVES("Panturrilha", 24),
    ABS("Abdômen", 24),
    FOREARMS("Antebraço", 24),
}

/** Carga por músculo: 1,0 quando é primário em algum exercício, 0,5 quando só aparece como secundário. */
typealias MuscleLoads = Map<Muscle, Double>

const val PRIMARY_LOAD = 1.0
const val SECONDARY_LOAD = 0.5

/** Junta as cargas de vários exercícios, ficando com a maior por músculo. */
fun loadsOf(exercises: List<Pair<List<Muscle>, List<Muscle>>>): MuscleLoads {
    val loads = mutableMapOf<Muscle, Double>()
    exercises.forEach { (primary, secondary) ->
        secondary.forEach { loads[it] = maxOf(loads[it] ?: 0.0, SECONDARY_LOAD) }
        primary.forEach { loads[it] = PRIMARY_LOAD }
    }
    return loads
}

/**
 * REST é um dia que virou descanso por decisão do usuário ou do recálculo;
 * a linha existe para o plano base não recriar o treino naquele dia.
 */
enum class SessionStatus { PLANNED, DONE, PARTIAL, MISSED, REST }

/** Como um dia aparece na faixa da semana e na tela Semana. */
enum class DayState { DONE, PARTIAL, MISSED, MOVED, TODAY, PLANNED, UNMARKED, REST, UNAVAILABLE }
