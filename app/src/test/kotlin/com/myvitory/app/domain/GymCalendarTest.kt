package com.myvitory.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class GymCalendarTest {
    @Test
    fun `junta dias da semana fechados e feriados, com o nome do feriado`() {
        val monday = LocalDate.of(2026, 11, 2)
        val closed = GymCalendar.closed(
            setOf(DayOfWeek.SUNDAY),
            mapOf(monday to "Finados", monday.plusDays(3) to ""),
            monday,
            monday.plusDays(6),
        )
        assertEquals(
            mapOf(monday to "Finados", monday.plusDays(3) to "Academia fechada", monday.plusDays(6) to "Academia fechada"),
            closed,
        )
    }
}
