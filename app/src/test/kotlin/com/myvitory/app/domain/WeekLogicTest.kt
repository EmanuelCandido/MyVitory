package com.myvitory.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekLogicTest {
    private val thursday = LocalDate.of(2026, 10, 29)
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun `semana começa na segunda`() {
        assertEquals(monday, WeekLogic.weekStartOf(thursday))
        assertEquals(monday, WeekLogic.weekStartOf(monday))
        assertEquals(monday, WeekLogic.weekStartOf(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun `cria só os dias com treino que ainda não existem`() {
        val plan = mapOf<DayOfWeek, Long?>(
            DayOfWeek.MONDAY to 1L,
            DayOfWeek.WEDNESDAY to 3L,
            DayOfWeek.SATURDAY to null,
        )
        val result = WeekLogic.sessionsToCreate(monday, plan, existingDates = setOf(monday))
        assertEquals(listOf(LocalDate.of(2026, 10, 28) to 3L), result)
    }

    @Test
    fun `recálculo só cobre a semana de hoje`() {
        assertTrue(WeekLogic.replanCovers(monday, thursday))
        assertTrue(WeekLogic.replanCovers(LocalDate.of(2026, 11, 1), thursday)) // domingo da mesma semana
        assertFalse(WeekLogic.replanCovers(monday.minusDays(1), monday)) // na segunda, o domingo é da semana passada
        assertFalse(WeekLogic.replanCovers(monday.plusWeeks(1), thursday))
    }

    @Test
    fun `status ao finalizar depende das séries marcadas`() {
        assertNull(WeekLogic.statusOnFinish(totalSets = 12, doneSets = 0))
        assertEquals(SessionStatus.PARTIAL, WeekLogic.statusOnFinish(12, 5))
        assertEquals(SessionStatus.DONE, WeekLogic.statusOnFinish(12, 12))
    }

    @Test
    fun `estado do dia`() {
        val yesterday = thursday.minusDays(1)
        val tomorrow = thursday.plusDays(1)
        assertEquals(DayState.REST, WeekLogic.dayState(thursday, thursday, null))
        assertEquals(DayState.TODAY, WeekLogic.dayState(thursday, thursday, SessionStatus.PLANNED))
        assertEquals(DayState.UNMARKED, WeekLogic.dayState(yesterday, thursday, SessionStatus.PLANNED))
        assertEquals(DayState.PLANNED, WeekLogic.dayState(tomorrow, thursday, SessionStatus.PLANNED))
        assertEquals(DayState.MISSED, WeekLogic.dayState(yesterday, thursday, SessionStatus.MISSED))
        assertEquals(DayState.DONE, WeekLogic.dayState(thursday, thursday, SessionStatus.DONE))
    }
}
