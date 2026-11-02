package com.myvitory.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** Dias em que a academia não abre: os dias da semana fechados e as datas marcadas (feriados). */
object GymCalendar {
    const val CLOSED = "Academia fechada"

    /** Data → motivo, entre [from] e [to]. Data marcada sem nome mostra "Academia fechada". */
    fun closed(closedWeekdays: Set<DayOfWeek>, closedDates: Map<LocalDate, String>, from: LocalDate, to: LocalDate): Map<LocalDate, String> {
        val result = linkedMapOf<LocalDate, String>()
        var date = from
        while (!date.isAfter(to)) {
            val label = closedDates[date] ?: CLOSED.takeIf { date.dayOfWeek in closedWeekdays }
            if (label != null) result[date] = label.ifBlank { CLOSED }
            date = date.plusDays(1)
        }
        return result
    }
}
