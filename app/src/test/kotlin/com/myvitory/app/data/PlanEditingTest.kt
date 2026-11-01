package com.myvitory.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.SessionStatus
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Editor de treino da v0.10: ordem, descanso, observação, apagar treino e o horário de cada mudança (para o sync). */
@RunWith(RobolectricTestRunner::class)
class PlanEditingTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val today = monday.plusDays(2) // quarta
    private var now = 1_000L

    private lateinit var db: MyVitoryDatabase
    private lateinit var repo: TrainingRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        repo = TrainingRepository(db, today = { today }, clock = { now })
        TrainingRepository(db, today = { monday }).seedIfEmpty()
        repo.ensureWeek(monday)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun legs() = db.templates().getBaseTemplates().first { it.name == "Pernas" }

    @Test
    fun `subir e descer exercício regrava a ordem e marca o treino como mudado`() = runBlocking {
        val legs = legs()
        val items = db.templates().getItems(legs.id)
        now = 5_000

        repo.moveTemplateItem(items[2].id, -1)
        repo.moveTemplateItem(items[0].id, -5) // já é o primeiro: não muda

        val order = db.templates().getItems(legs.id).map { it.exerciseId }
        assertEquals(listOf(items[0], items[2], items[1], items[3], items[4]).map { it.exerciseId }, order)
        assertEquals(listOf(0, 1, 2, 3, 4), db.templates().getItems(legs.id).map { it.position })
        assertEquals(5_000L, db.templates().getTemplate(legs.id)!!.updatedAt)
    }

    @Test
    fun `descanso e observação ficam no treino e aparecem na tela do treino`() = runBlocking {
        val legs = legs()
        val first = db.templates().getItems(legs.id).first()

        repo.setItemRest(first.id, 2_000)
        repo.setItemNote(first.id, "  Descer até 90 graus  ")

        val saved = db.templates().getItem(first.id)!!
        assertEquals(MAX_REST_SEC, saved.restSec)
        assertEquals("Descer até 90 graus", saved.note)
        val session = repo.observeWeek(monday).first().first { it.date == today }.session!!
        repo.startWorkout(session.id)
        val workout = repo.observeWorkout(session.id).first()!!
        assertEquals("Descer até 90 graus", workout.exercises.first().note)
        assertEquals(MAX_REST_SEC, workout.exercises.first().sets.first().restSec)
    }

    @Test
    fun `abrir o treino duas vezes ao mesmo tempo não cria séries em dobro`() = runBlocking {
        val session = repo.observeWeek(monday).first().first { it.date == today }.session!!

        listOf(async { repo.startWorkout(session.id) }, async { repo.startWorkout(session.id) }).awaitAll()

        assertEquals(17, db.setLogs().countForSession(session.id))
    }

    @Test
    fun `apagar treino deixa o dia do plano em descanso e tira dos próximos dias`() = runBlocking {
        val legs = legs()
        now = 3_000

        assertTrue(repo.deleteTemplate(legs.id))

        assertNull(db.plan().getPlan().first { it.dayOfWeek == DayOfWeek.WEDNESDAY.value }.templateId)
        assertEquals(3_000L, db.plan().getPlan().first { it.dayOfWeek == DayOfWeek.WEDNESDAY.value }.updatedAt)
        assertNull(repo.observeWeek(monday).first().first { it.date == today }.session)
        assertTrue(db.sync().tombstones().any { it.key == templateKey(legs.remoteId) })
    }

    @Test
    fun `cópia só desta semana não é apagada pelo editor`() = runBlocking {
        val session = repo.observeWeek(monday).first().first { it.date == today }.session!!
        val copyId = repo.customizeDayThisWeek(session.id)!!

        assertFalse(repo.deleteTemplate(copyId))
        assertEquals(SessionStatus.PLANNED, db.sessions().get(session.id)!!.status)
    }

    @Test
    fun `exercício próprio ganha id aleatório e apagar deixa registro para o sync`() = runBlocking {
        val first = repo.createExercise("Coice", "Polia", listOf(com.myvitory.app.domain.Muscle.GLUTES), emptyList())
        val second = repo.createExercise("Coice 2", "Polia", listOf(com.myvitory.app.domain.Muscle.GLUTES), emptyList())

        assertTrue(first.startsWith("custom_") && first != second)
        assertTrue(repo.deleteExercise(first))
        assertTrue(db.sync().tombstones().any { it.key == exerciseKey(first) })
    }
}
