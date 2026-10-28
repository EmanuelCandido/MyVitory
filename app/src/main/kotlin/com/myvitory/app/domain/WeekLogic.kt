package com.myvitory.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Regras puras da semana, sem Android, para poderem ser testadas direto na JVM. */
object WeekLogic {

    fun weekStartOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun daysOf(weekStart: LocalDate): List<LocalDate> = (0L..6L).map { weekStart.plusDays(it) }

    /** Dias da semana que têm treino no plano e ainda não têm sessão criada. */
    fun sessionsToCreate(
        weekStart: LocalDate,
        plan: Map<DayOfWeek, Long?>,
        existingDates: Set<LocalDate>,
    ): List<Pair<LocalDate, Long>> =
        daysOf(weekStart).mapNotNull { date ->
            val templateId = plan[date.dayOfWeek] ?: return@mapNotNull null
            if (date in existingDates) null else date to templateId
        }

    /**
     * Status ao finalizar o treino pelo botão "Finalizar".
     * Nenhuma série marcada devolve null: não dá para afirmar que treinou, então o app pergunta.
     */
    fun statusOnFinish(totalSets: Int, doneSets: Int): SessionStatus? = when {
        doneSets <= 0 -> null
        doneSets >= totalSets -> SessionStatus.DONE
        else -> SessionStatus.PARTIAL
    }

    fun dayState(
        date: LocalDate,
        today: LocalDate,
        status: SessionStatus?,
        moved: Boolean = false,
        unavailable: Boolean = false,
    ): DayState = when {
        // Feito vale mais que o aviso; o resto do dia indisponível aparece como indisponível.
        unavailable && status != SessionStatus.DONE && status != SessionStatus.PARTIAL && !moved -> DayState.UNAVAILABLE
        else -> stateOf(date, today, status, moved)
    }

    private fun stateOf(date: LocalDate, today: LocalDate, status: SessionStatus?, moved: Boolean): DayState = when (status) {
        null, SessionStatus.REST -> DayState.REST
        SessionStatus.DONE -> DayState.DONE
        SessionStatus.PARTIAL -> DayState.PARTIAL
        SessionStatus.MISSED -> if (moved) DayState.MOVED else DayState.MISSED
        SessionStatus.PLANNED -> when {
            date == today -> DayState.TODAY
            date.isBefore(today) -> DayState.UNMARKED
            else -> DayState.PLANNED
        }
    }
}
