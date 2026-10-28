package com.myvitory.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {
    private fun sets(vararg pairs: Pair<Double, Int>) = pairs.map { DoneSet(it.first, it.second) }

    @Test
    fun `bateu todas as repetições, sobe um degrau`() {
        val s = Progression.suggest(sets(100.0 to 8, 100.0 to 8, 100.0 to 9), targetReps = 8, targetSets = 3, equipment = "Barra")
        assertEquals(102.5, s!!.weightKg, 0.0)
        assertEquals(2.5, s.increaseKg, 0.0)
    }

    @Test
    fun `halteres sobem de 2 em 2 kg`() {
        assertEquals(22.0, Progression.suggest(sets(20.0 to 10, 20.0 to 10), 10, 2, "Halteres")!!.weightKg, 0.0)
    }

    @Test
    fun `não sobe quando falta repetição, série ou a carga variou`() {
        assertNull("faltou repetição", Progression.suggest(sets(100.0 to 8, 100.0 to 7), 8, 2, "Barra"))
        assertNull("fez só parte das séries", Progression.suggest(sets(100.0 to 8), 8, 4, "Barra"))
        assertNull("carga diferente entre séries", Progression.suggest(sets(100.0 to 8, 95.0 to 8), 8, 2, "Barra"))
        assertNull("peso corporal", Progression.suggest(sets(0.0 to 12, 0.0 to 12), 12, 2, "Peso corporal"))
        assertNull("sem carga registrada", Progression.suggest(sets(0.0 to 10, 0.0 to 10), 10, 2, "Máquina"))
        assertNull("sem histórico", Progression.suggest(emptyList(), 10, 3, "Máquina"))
    }
}
