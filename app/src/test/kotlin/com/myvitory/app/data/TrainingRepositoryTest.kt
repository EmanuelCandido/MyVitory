package com.myvitory.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrainingRepositoryTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private var today = monday.plusDays(3) // quinta
    private var now = 1_000L

    private lateinit var db: MyVitoryDatabase
    private lateinit var repo: TrainingRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        repo = TrainingRepository(db, today = { today }, clock = { now })
        repo.seedIfEmpty()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun week(start: LocalDate = monday) = repo.observeWeek(start).first()

    private suspend fun sessionOn(date: LocalDate) = week(WeekLogic.weekStartOf(date)).first { it.date == date }.session!!

    @Test
    fun `semana de exemplo tem treino de segunda a sexta e não duplica`() = runBlocking {
        repo.seedIfEmpty()
        repo.ensureWeek(monday)
        repo.ensureWeek(monday)
        val days = week()
        assertEquals(listOf("Peito + Tríceps", "Costas + Bíceps", "Pernas", "Ombros + Abdômen", "Posterior + Glúteos", null, null), days.map { it.session?.name })
        assertEquals(5, days.first().exerciseCount)
    }

    @Test
    fun `começar treino cria as séries e usa a carga da última vez`() = runBlocking {
        repo.ensureWeek(monday)
        val wednesday = monday.plusDays(2)
        val legs = sessionOn(wednesday)
        repo.startWorkout(legs.id)

        val first = repo.observeWorkout(legs.id).first()!!
        assertEquals(4 + 3 + 3 + 3 + 4, first.totalSets)
        val squat = first.exercises.first().sets.first()
        repo.updateSet(squat.copy(weightKg = 100.0, done = true))
        assertEquals(SessionStatus.PARTIAL, repo.finishWorkout(legs.id))

        val nextWeek = monday.plusWeeks(1)
        today = nextWeek.plusDays(2)
        repo.ensureWeek(nextWeek)
        val nextLegs = sessionOn(nextWeek.plusDays(2))
        repo.startWorkout(nextLegs.id)
        val next = repo.observeWorkout(nextLegs.id).first()!!
        val nextSquat = next.exercises.first()
        assertTrue(nextSquat.sets.all { it.weightKg == 100.0 })
        assertEquals("100 kg × 8", nextSquat.lastTime)
    }

    @Test
    fun `finalizar sem séries marcadas não muda o status`() = runBlocking {
        repo.ensureWeek(monday)
        val thursday = sessionOn(today)
        repo.startWorkout(thursday.id)
        assertNull(repo.finishWorkout(thursday.id))
        assertEquals(SessionStatus.PLANNED, sessionOn(today).status)
    }

    @Test
    fun `marcar como feito e desfazer`() = runBlocking {
        repo.ensureWeek(monday)
        val thursday = sessionOn(today)
        repo.startWorkout(thursday.id)

        val undo = repo.markSession(thursday.id, SessionStatus.DONE)
        assertNotNull(undo)
        assertEquals(SessionStatus.DONE, sessionOn(today).status)
        assertTrue(repo.observeWorkout(thursday.id).first()!!.exercises.all { ex -> ex.sets.all { it.done } })

        repo.undoMark(undo!!)
        assertEquals(SessionStatus.PLANNED, sessionOn(today).status)
        assertEquals(0, repo.observeWorkout(thursday.id).first()!!.doneSets)
    }

    @Test
    fun `mudar o plano base refaz só os dias que ninguém começou`() = runBlocking {
        repo.ensureWeek(monday)
        val friday = sessionOn(today.plusDays(1))
        repo.startWorkout(friday.id)

        repo.setPlanDay(DayOfWeek.THURSDAY, null)
        repo.setPlanDay(DayOfWeek.FRIDAY, null)
        repo.setPlanDay(DayOfWeek.SATURDAY, friday.templateId)

        val days = week()
        assertNull(days[3].session) // quinta virou descanso
        assertEquals(friday.id, days[4].session?.id) // sexta já tinha começado: fica
        assertEquals("Posterior + Glúteos", days[5].session?.name) // sábado ganhou treino
        assertEquals(SessionStatus.PLANNED, days[0].session?.status) // passado não mexe
    }

    @Test
    fun `trocar exercício no meio do treino mantém as séries feitas`() = runBlocking {
        repo.ensureWeek(monday)
        val wednesday = sessionOn(monday.plusDays(2)) // Pernas: agachamento livre 4 séries
        repo.startWorkout(wednesday.id)
        val squat = repo.observeWorkout(wednesday.id).first()!!.exercises.first()
        repo.updateSet(squat.sets.first().copy(done = true, weightKg = 80.0))

        val undo = repo.swapExercise(wednesday.id, "agachamento_livre", "agachamento_hack", alsoInPlan = false)!!
        val after = repo.observeWorkout(wednesday.id).first()!!
        val (original, replacement) = after.exercises.take(2)
        assertEquals("agachamento_livre", original.exerciseId)
        assertEquals(1, original.sets.size)
        assertTrue(original.sets.single().done)
        assertEquals("agachamento_hack", replacement.exerciseId)
        assertEquals(3, replacement.sets.size)
        assertEquals(original.position, replacement.position)
        // Plano não mudou.
        assertEquals("agachamento_livre", repo.observeTemplateItems(wednesday.templateId!!).first().first().exerciseId)

        repo.undoSwap(undo)
        val restored = repo.observeWorkout(wednesday.id).first()!!.exercises.first()
        assertEquals("agachamento_livre", restored.exerciseId)
        assertEquals(4, restored.sets.size)
    }

    @Test
    fun `trocar no plano todo vale para as próximas semanas`() = runBlocking {
        repo.ensureWeek(monday)
        val wednesday = sessionOn(monday.plusDays(2))
        repo.startWorkout(wednesday.id)
        val undo = repo.swapExercise(wednesday.id, "leg_press_45", "agachamento_hack", alsoInPlan = true)!!
        val items = repo.observeTemplateItems(wednesday.templateId!!).first()
        assertEquals("agachamento_hack", items[1].exerciseId)
        repo.undoSwap(undo)
        assertEquals("leg_press_45", repo.observeTemplateItems(wednesday.templateId).first()[1].exerciseId)
    }
}
