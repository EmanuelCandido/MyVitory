package com.myvitory.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightLogicTest {
    private val today = LocalDate.of(2026, 10, 30) // sexta

    private fun w(daysAgo: Long, kg: Double) = Weighing(today.minusDays(daysAgo), kg)

    @Test
    fun `pesagem vence depois de 30 dias e sem nenhuma`() {
        assertTrue(WeightLogic.isDue(emptyList(), today))
        assertFalse(WeightLogic.isDue(listOf(w(29, 80.0)), today))
        assertTrue(WeightLogic.isDue(listOf(w(30, 80.0)), today))
        assertEquals(12L, WeightLogic.daysSinceLast(listOf(w(40, 81.0), w(12, 80.0)), today))
    }

    @Test
    fun `notificação no 30o dia e depois a cada semana`() {
        assertTrue(WeightLogic.shouldNotify(listOf(w(30, 80.0)), today))
        assertFalse(WeightLogic.shouldNotify(listOf(w(31, 80.0)), today))
        assertTrue(WeightLogic.shouldNotify(listOf(w(37, 80.0)), today))
        assertFalse(WeightLogic.shouldNotify(listOf(w(10, 80.0)), today))
        // Nunca pesou: só às segundas.
        assertFalse(WeightLogic.shouldNotify(emptyList(), today))
        assertTrue(WeightLogic.shouldNotify(emptyList(), LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `tendência precisa de duas pesagens com uma semana de distância`() {
        assertNull(WeightLogic.trend(listOf(w(0, 80.0))))
        assertNull(WeightLogic.trend(listOf(w(5, 81.0), w(0, 80.0))))
        assertNotNull(WeightLogic.trend(listOf(w(7, 81.0), w(0, 80.0))))
    }

    @Test
    fun `tendência é a reta das pesagens recentes`() {
        // Perde 0,5 kg por semana, com uma pesagem fora da reta no meio.
        val weights = listOf(w(28, 82.0), w(21, 81.5), w(14, 81.3), w(7, 80.5), w(0, 80.0))
        val trend = WeightLogic.trend(weights)!!
        assertEquals(-0.5, trend.kgPerWeek, 0.06)
        assertEquals(5, trend.count)
    }

    @Test
    fun `pesagens de mais de 90 dias não entram na tendência`() {
        val weights = listOf(w(200, 60.0), w(14, 80.0), w(0, 80.0))
        val trend = WeightLogic.trend(weights)!!
        assertEquals(0.0, trend.kgPerWeek, 1e-9)
        assertEquals(2, trend.count)
    }

    @Test
    fun `previsão parte da última pesagem e anda 30 dias a partir de hoje`() {
        val weights = listOf(w(28, 82.0), w(0, 80.0)) // -2 kg em 28 dias
        val forecast = WeightLogic.forecast(weights, today)!!
        assertEquals(today.plusDays(30), forecast.date)
        assertEquals(80.0 - 2.0 / 28 * 30, forecast.kg, 1e-9)
    }

    @Test
    fun `sem previsão quando a última pesagem é velha demais`() {
        assertNull(WeightLogic.forecast(listOf(w(100, 82.0), w(61, 80.0)), today))
        assertNotNull(WeightLogic.forecast(listOf(w(100, 82.0), w(60, 80.0)), today))
    }

    @Test
    fun `meta de perder peso no caminho certo`() {
        val goal = WeightGoal(targetKg = 76.0, startKg = 82.0, by = today.plusDays(56))
        val weights = listOf(w(28, 82.0), w(0, 80.0)) // -0,5 kg/semana
        val p = WeightLogic.progress(goal, weights, today)!!
        assertEquals(4.0, p.remainingKg, 1e-9)
        assertEquals(2.0 / 6.0, p.fraction, 1e-9)
        assertFalse(p.reached)
        assertEquals(today.plusDays(56), p.eta) // 4 kg ÷ (2/28 kg por dia) = 56 dias
        assertEquals(-0.5, p.neededKgPerWeek!!, 1e-9) // 4 kg em 8 semanas
    }

    @Test
    fun `meta sem previsão quando o peso vai para o outro lado`() {
        val goal = WeightGoal(targetKg = 76.0, startKg = 80.0)
        val p = WeightLogic.progress(goal, listOf(w(14, 80.0), w(0, 81.0)), today)!!
        assertNull(p.eta)
        assertEquals(0.0, p.fraction, 1e-9)
        assertEquals(5.0, p.remainingKg, 1e-9)
    }

    @Test
    fun `meta de ganhar peso alcançada`() {
        val goal = WeightGoal(targetKg = 70.0, startKg = 65.0)
        val p = WeightLogic.progress(goal, listOf(w(30, 65.0), w(0, 70.4)), today)!!
        assertTrue(p.reached)
        assertEquals(1.0, p.fraction, 1e-9)
        assertEquals(0.0, p.remainingKg, 1e-9)
        assertNull(p.eta)
    }
}
