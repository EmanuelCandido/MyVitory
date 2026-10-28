package com.myvitory.app.domain

import com.myvitory.app.data.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoAndAlternativesTest {
    @Test
    fun `aceita os formatos de link do YouTube`() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=30",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            id,
        ).forEach { assertEquals(it, id, VideoLinks.parseId(it)) }
        assertNull(VideoLinks.parseId("https://vimeo.com/123456"))
        assertNull(VideoLinks.parseId("supino"))
    }

    @Test
    fun `duração ISO da API`() {
        assertEquals(340, VideoLinks.parseDuration("PT5M40S"))
        assertEquals(3725, VideoLinks.parseDuration("PT1H2M5S"))
        assertNull(VideoLinks.parseDuration("lixo"))
        assertEquals("5:40", VideoLinks.formatDuration(340))
        assertEquals("1:02:05", VideoLinks.formatDuration(3725))
    }

    private val catalog = SeedData.exercises.map { ExerciseRef(it.id, it.primary, it.secondary, it.equipment) }

    @Test
    fun `alternativas trabalham o mesmo músculo principal`() {
        val legPress = catalog.first { it.id == "leg_press_45" }
        val alternatives = Alternatives.rank(legPress, catalog).map { it.id }
        assertTrue(alternatives.containsAll(listOf("agachamento_hack", "agachamento_bulgaro", "cadeira_extensora")))
        assertFalse("leg_press_45" in alternatives)
        assertTrue(alternatives.none { id -> catalog.first { it.id == id }.primary.none { it == Muscle.QUADS } })
        // Mesmos músculos (principal e de apoio) vêm primeiro; entre eles, outro equipamento ganha (aparelho ocupado).
        assertEquals(listOf("agachamento_bulgaro", "agachamento_hack"), alternatives.take(2))
    }

    @Test
    fun `todo exercício do catálogo tem pontos-chave e ao menos uma alternativa`() {
        SeedData.exercises.forEach { exercise ->
            assertEquals(exercise.id, 3, com.myvitory.app.data.ExerciseTips.forExercise(exercise.id).size)
            val ref = catalog.first { it.id == exercise.id }
            assertTrue(exercise.id, Alternatives.rank(ref, catalog).isNotEmpty())
        }
    }
}
