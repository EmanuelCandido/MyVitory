package com.myvitory.app.domain.planner

import com.myvitory.app.domain.Muscle.ABS
import com.myvitory.app.domain.Muscle.BACK
import com.myvitory.app.domain.Muscle.BICEPS
import com.myvitory.app.domain.Muscle.CALVES
import com.myvitory.app.domain.Muscle.CHEST
import com.myvitory.app.domain.Muscle.GLUTES
import com.myvitory.app.domain.Muscle.HAMSTRINGS
import com.myvitory.app.domain.Muscle.QUADS
import com.myvitory.app.domain.Muscle.SHOULDERS
import com.myvitory.app.domain.Muscle.TRICEPS
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cenário da prancheta: semana ABCDE, Pernas (qua) não foi feito, hoje é quinta. */
class PlannerTest {
    private val mon = LocalDate.of(2026, 10, 26)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val thu = mon.plusDays(3)
    private val fri = mon.plusDays(4)
    private val sat = mon.plusDays(5)
    private val sun = mon.plusDays(6)

    private val chestTri = mapOf(CHEST to 1.0, TRICEPS to 1.0, SHOULDERS to 0.5)
    private val backBi = mapOf(BACK to 1.0, BICEPS to 1.0)
    private val legs = mapOf(QUADS to 1.0, HAMSTRINGS to 1.0, CALVES to 1.0, GLUTES to 0.5)
    private val shouldersAbs = mapOf(SHOULDERS to 1.0, ABS to 1.0, TRICEPS to 0.5, BACK to 0.5)
    private val posterior = mapOf(HAMSTRINGS to 1.0, GLUTES to 1.0, QUADS to 1.0)

    private val missedLegs = Workout(3, 3, "Pernas", legs, wed, missed = true)
    private val thursday = Workout(4, 4, "Ombros + Abdômen", shouldersAbs, thu, missed = false)
    private val friday = Workout(5, 5, "Posterior + Glúteos", posterior, fri, missed = false)

    private val input = PlannerInput(
        pending = listOf(missedLegs, thursday, friday),
        slots = listOf(Slot(thu, false), Slot(fri, false), Slot(sat, true), Slot(sun, true)),
        history = listOf(Training(mon, "Peito + Tríceps", chestTri), Training(tue, "Costas + Bíceps", backBi)),
        nextWeek = listOf(
            Training(mon.plusWeeks(1), "Peito + Tríceps", chestTri),
            Training(tue.plusWeeks(1), "Costas + Bíceps", backBi),
            Training(wed.plusWeeks(1), "Pernas", legs),
        ),
    )

    @Test
    fun `recomendada encaixa Pernas e respeita 72 h antes do posterior`() {
        val result = Planner.replan(input)
        val best = result.best
        assertEquals(thu, best.dateOf(missedLegs))
        assertTrue(best.dropped.isEmpty())
        // Posterior precisa de 72 h depois de Pernas: vai para domingo, não sábado.
        assertEquals(sun, best.dateOf(friday))
        assertEquals(fri, best.dateOf(thursday))
    }

    @Test
    fun `alternativa não usa dias de descanso`() {
        val alternative = Planner.replan(input).alternative
        assertNotNull(alternative)
        assertTrue(alternative!!.assignment.keys.none { it == sat || it == sun })
    }

    @Test
    fun `empurrar tudo um dia custa mais que a recomendada`() {
        val naive = mapOf(thu to missedLegs, fri to thursday, sat to friday)
        val best = Planner.replan(input).best
        assertTrue(Planner.cost(input, naive) > best.cost)
    }

    @Test
    fun `explicação lista só os dias que mudaram`() {
        val best = Planner.replan(input).best
        val explanation = Planner.explain(input, best)
        assertEquals(listOf(thu, fri, sun), explanation.changes.map { it.date })
        val sunday = explanation.changes.last()
        assertTrue(sunday.usesRestDay)
        assertEquals("Posterior + Glúteos", sunday.after?.name)
        assertNotNull(explanation.nextRest)
        assertTrue(explanation.nextRest!!.ok)
    }

    @Test
    fun `semana sem dias livres pula o treino perdido`() {
        val tight = input.copy(pending = listOf(missedLegs), slots = emptyList())
        val result = Planner.replan(tight)
        assertNull(result.best.dateOf(missedLegs))
        assertEquals(listOf(missedLegs), result.best.dropped)
        assertFalse(result.alternative != null && result.alternative == result.best)
    }

    @Test
    fun `sequência longa custa`() {
        val days = setOf(mon, tue, wed, thu, fri)
        assertEquals(5, Planner.longestStreak(days))
        assertEquals(2, Planner.longestStreak(setOf(mon, tue, thu)))
    }
}
