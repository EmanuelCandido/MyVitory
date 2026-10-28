package com.myvitory.app.domain

/** Um treino feito, com o horário real em que terminou. */
data class TrainingEvent(val atMillis: Long, val loads: MuscleLoads)

data class MuscleRecovery(
    val muscle: Muscle,
    val lastAtMillis: Long?,
    /** 0 a 100. */
    val percent: Int,
    val readyAtMillis: Long?,
) {
    val ready get() = percent >= 100
}

object Recovery {
    private const val HOUR = 3_600_000L

    /** Recuperação de cada músculo agora: horas desde o último treino ÷ (janela × carga). */
    fun status(
        events: List<TrainingEvent>,
        nowMillis: Long,
        recoveryHours: (Muscle) -> Int = { it.recoveryHours },
    ): List<MuscleRecovery> = Muscle.entries.map { muscle ->
        val last = events.filter { muscle in it.loads }.maxByOrNull { it.atMillis }
        if (last == null) {
            MuscleRecovery(muscle, null, 100, null)
        } else {
            val windowMillis = (recoveryHours(muscle) * last.loads.getValue(muscle) * HOUR).toLong()
            val elapsed = (nowMillis - last.atMillis).coerceAtLeast(0)
            val percent = if (windowMillis <= 0) 100 else ((elapsed * 100) / windowMillis).toInt().coerceAtMost(100)
            MuscleRecovery(muscle, last.atMillis, percent, last.atMillis + windowMillis)
        }
    }
}
