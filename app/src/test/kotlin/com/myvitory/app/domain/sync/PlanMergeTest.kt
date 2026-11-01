package com.myvitory.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanMergeTest {
    private val now = 1_000_000L

    private fun template(name: String, at: Long, vararg exercises: String) =
        SnapshotTemplate("A", name, at, exercises.map { SnapshotItem(it, 3, 10, 90) })

    @Test
    fun `cada treino fica com a versão mais recente e nenhum lado perde treino novo`() {
        val local = PlanSnapshot(templates = mapOf("a" to template("Peito celular", 300, "supino_reto"), "b" to template("Só no celular", 100)))
        val remote = PlanSnapshot(templates = mapOf("a" to template("Peito painel", 200, "crossover"), "c" to template("Só no painel", 100)))

        val merged = PlanMerge.merge(local, remote, now)

        assertEquals("Peito celular", merged.templates.getValue("a").name)
        assertEquals(setOf("a", "b", "c"), merged.templates.keys)
    }

    @Test
    fun `empate fica com a nuvem`() {
        val merged = PlanMerge.merge(
            PlanSnapshot(templates = mapOf("a" to template("Celular", 100))),
            PlanSnapshot(templates = mapOf("a" to template("Nuvem", 100))),
            now,
        )
        assertEquals("Nuvem", merged.templates.getValue("a").name)
    }

    @Test
    fun `apagar vale contra mudança mais velha e perde para mudança mais nova`() {
        val local = PlanSnapshot(templates = mapOf("a" to template("A", 100), "b" to template("B", 500)))
        val remote = PlanSnapshot(deleted = mapOf("template:a" to 200, "template:b" to 400))

        val merged = PlanMerge.merge(local, remote, now)

        assertFalse("a" in merged.templates)
        assertEquals(200L, merged.deleted["template:a"])
        // B mudou no celular depois de apagado no painel: volta, e o apagamento sai da lista.
        assertTrue("b" in merged.templates)
        assertNull(merged.deleted["template:b"])
    }

    @Test
    fun `dia da semana fica com a escolha mais recente e treino apagado vira descanso`() {
        val local = PlanSnapshot(
            templates = mapOf("a" to template("A", 1)),
            week = mapOf(1 to SnapshotDay("a", 50), 2 to SnapshotDay("a", 10)),
        )
        val remote = PlanSnapshot(
            week = mapOf(1 to SnapshotDay(null, 40), 2 to SnapshotDay("x", 20)),
            deleted = mapOf("template:x" to 30),
        )

        val merged = PlanMerge.merge(local, remote, now)

        assertEquals(SnapshotDay("a", 50), merged.week[1])
        assertEquals(SnapshotDay(null, 20), merged.week[2])
    }

    @Test
    fun `os dois lados chegam ao mesmo plano`() {
        val a = PlanSnapshot(
            templates = mapOf("a" to template("A1", 10), "b" to template("B2", 30)),
            week = mapOf(1 to SnapshotDay("a", 5)),
            exercises = mapOf("custom_1" to SnapshotExercise("Abdução", "Polia", listOf("GLUTES"), emptyList(), 7)),
        )
        val b = PlanSnapshot(
            templates = mapOf("a" to template("A2", 20), "b" to template("B1", 25)),
            week = mapOf(1 to SnapshotDay("b", 6)),
            deleted = mapOf("exercise:custom_1" to 8),
        )
        val ab = PlanMerge.merge(a, b, now)
        assertEquals(ab, PlanMerge.merge(b, a, now))
        assertEquals("A2", ab.templates.getValue("a").name)
        assertEquals("B2", ab.templates.getValue("b").name)
        assertTrue(ab.exercises.isEmpty())
        // Juntar de novo com o resultado não muda nada (o sync para de escrever).
        assertEquals(ab, PlanMerge.merge(ab, ab, now))
    }

    @Test
    fun `apagamento antigo sai da lista depois de 90 dias`() {
        val old = now - PlanMerge.TOMBSTONE_TTL_MS - 1
        val merged = PlanMerge.merge(PlanSnapshot(deleted = mapOf("template:velho" to old, "template:novo" to now - 5)), PlanSnapshot(), now)
        assertEquals(setOf("template:novo"), merged.deleted.keys)
    }
}
