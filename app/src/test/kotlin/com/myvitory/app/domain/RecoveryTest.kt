package com.myvitory.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecoveryTest {
    private val hour = 3_600_000L

    @Test
    fun `porcentagem pela janela e pela carga`() {
        val now = 100 * hour
        val events = listOf(
            TrainingEvent(now - 36 * hour, mapOf(Muscle.BACK to 1.0, Muscle.BICEPS to 1.0)),
            TrainingEvent(now - 12 * hour, mapOf(Muscle.TRICEPS to 0.5)),
        )
        val byMuscle = Recovery.status(events, now).associateBy { it.muscle }
        assertEquals(50, byMuscle.getValue(Muscle.BACK).percent) // 36 de 72 h
        assertEquals(75, byMuscle.getValue(Muscle.BICEPS).percent) // 36 de 48 h
        assertEquals(50, byMuscle.getValue(Muscle.TRICEPS).percent) // secundário: 12 de 24 h
        assertEquals(now + 36 * hour, byMuscle.getValue(Muscle.BACK).readyAtMillis)
        val quads = byMuscle.getValue(Muscle.QUADS)
        assertEquals(100, quads.percent)
        assertNull(quads.lastAtMillis)
    }
}
