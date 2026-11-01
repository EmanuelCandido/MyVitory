package com.myvitory.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        TrainingRepository(db, today = { monday }).seedIfEmpty() // instalado na segunda: a semana toda tem treino
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
    fun `instalar na quinta não conta segunda a quarta como falta`() = runBlocking {
        val fresh = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        val freshRepo = TrainingRepository(fresh, today = { today }, clock = { now })
        freshRepo.seedIfEmpty()
        freshRepo.ensureWeek(monday)
        val days = freshRepo.observeWeek(monday).first()
        assertEquals(listOf(null, null, null, "Ombros + Abdômen", "Posterior + Glúteos"), days.take(5).map { it.session?.name })
        assertTrue(days.take(3).none { it.state == com.myvitory.app.domain.DayState.UNMARKED })
        fresh.close()
    }

    @Test
    fun `mudança só nesta semana não mexe no plano base e sobrevive a ele`() = runBlocking {
        repo.ensureWeek(monday)
        val friday = today.plusDays(1) // Posterior + Glúteos
        val saturday = today.plusDays(2) // descanso
        val legsId = sessionOn(monday.plusDays(2)).templateId!!

        // Sábado ganha Pernas só nesta semana; sexta vira descanso.
        assertTrue(repo.setDayThisWeek(saturday, legsId))
        assertTrue(repo.setDayThisWeek(friday, null))
        assertFalse(repo.setDayThisWeek(monday, legsId)) // passado não muda
        var days = week()
        assertNull(days[4].session)
        assertTrue(days[4].special)
        assertEquals("Pernas", days[5].session?.name)

        // Mexer no plano base refaz os dias normais, mas não os especiais.
        repo.setPlanDay(DayOfWeek.SUNDAY, legsId)
        days = week()
        assertNull(days[4].session)
        assertEquals("Pernas", days[5].session?.name)
        assertEquals("Pernas", days[6].session?.name)

        // Exercícios só deste dia: a cópia muda, o treino original não.
        val thursday = sessionOn(today)
        val originalItems = repo.observeTemplateItems(thursday.templateId!!).first()
        val copyId = repo.customizeDayThisWeek(thursday.id)!!
        repo.removeTemplateItem(repo.observeTemplateItems(copyId).first().first().id)
        assertEquals(originalItems.size - 1, week()[3].exerciseCount)
        assertEquals(originalItems.size, repo.observeTemplateItems(thursday.templateId).first().size)
        assertTrue(repo.observeTemplates().first().none { it.template.id == copyId })

        // Na semana seguinte, quinta volta ao normal.
        repo.ensureWeek(monday.plusWeeks(1))
        assertEquals(thursday.templateId, sessionOn(today.plusWeeks(1)).templateId)

        // Voltar ao plano base desfaz e apaga a cópia.
        assertTrue(repo.resetDayToPlan(today))
        assertEquals(thursday.templateId, sessionOn(today).templateId)
        assertNull(db.templates().getTemplate(copyId))
        assertTrue(repo.resetDayToPlan(friday))
        assertEquals("Posterior + Glúteos", week()[4].session?.name)
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
    fun `registrar depois um dia que passou não carimba o horário de agora`() = runBlocking {
        repo.ensureWeek(monday)
        val wednesday = monday.plusDays(2)
        val legs = sessionOn(wednesday)
        now = 5_000L
        repo.startWorkout(legs.id)
        repo.observeWorkout(legs.id).first()!!.exercises.flatMap { it.sets }.forEach { repo.updateSet(it.copy(done = true)) }
        assertEquals(SessionStatus.DONE, repo.finishWorkout(legs.id))
        assertNull(sessionOn(wednesday).startedAt)
        assertNull(sessionOn(wednesday).finishedAt)

        // O treino de hoje continua com horário.
        val thursday = sessionOn(today)
        repo.startWorkout(thursday.id)
        assertEquals(5_000L, sessionOn(today).startedAt)
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
    fun `progressão sobe a carga quando o treino anterior foi completo`() = runBlocking {
        repo.ensureWeek(monday)
        val wednesday = sessionOn(monday.plusDays(2))
        repo.startWorkout(wednesday.id)
        val legPress = repo.observeWorkout(wednesday.id).first()!!.exercises[1] // Leg press 45°: 3 × 10
        legPress.sets.forEach { repo.updateSet(it.copy(weightKg = 140.0, done = true)) }

        val nextWeek = monday.plusWeeks(1)
        today = nextWeek.plusDays(2)
        repo.ensureWeek(nextWeek)
        val next = sessionOn(nextWeek.plusDays(2))
        repo.startWorkout(next.id)
        val nextLegPress = repo.observeWorkout(next.id).first()!!.exercises[1]
        assertTrue(nextLegPress.sets.all { it.weightKg == 142.5 })
        assertEquals(2.5, nextLegPress.suggestion!!.increaseKg, 0.0)
        // Agachamento não foi feito na semana anterior: sem sugestão.
        assertNull(repo.observeWorkout(next.id).first()!!.exercises[0].suggestion)
    }

    @Test
    fun `histórico do exercício em ordem cronológica`() = runBlocking {
        repo.ensureWeek(monday)
        val wednesday = sessionOn(monday.plusDays(2))
        repo.startWorkout(wednesday.id)
        val legPress = repo.observeWorkout(wednesday.id).first()!!.exercises[1]
        legPress.sets.forEachIndexed { i, s -> repo.updateSet(s.copy(weightKg = 130.0 + i * 5, done = true)) }
        val history = repo.observeHistory("leg_press_45").first()
        assertEquals(1, history.size)
        assertEquals(140.0, history.single().topKg, 0.0)
        assertEquals((130 + 135 + 140) * 10.0, history.single().volumeKg, 0.0)
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

    @Test
    fun `trocar no plano todo num dia fundido muda o treino de onde o exercício veio`() = runBlocking {
        repo.ensureWeek(monday)
        val legsId = sessionOn(monday.plusDays(2)).templateId!!
        val thursday = sessionOn(today) // Ombros + Abdômen, com Pernas fundido
        db.sessions().update(thursday.copy(mergedTemplateId = legsId, name = "Pernas + Ombros + Abdômen"))
        repo.startWorkout(thursday.id)

        val undo = repo.swapExercise(thursday.id, "agachamento_livre", "agachamento_hack", alsoInPlan = true)!!
        assertTrue(undo.changedBasePlan)
        assertEquals("agachamento_hack", repo.observeTemplateItems(legsId).first().first().exerciseId)
        repo.undoSwap(undo)
        assertEquals("agachamento_livre", repo.observeTemplateItems(legsId).first().first().exerciseId)
    }

    @Test
    fun `trocar no plano todo num dia só nesta semana muda também o plano base`() = runBlocking {
        repo.ensureWeek(monday)
        val thursday = sessionOn(today)
        val baseId = thursday.templateId!!
        val copyId = repo.customizeDayThisWeek(thursday.id)!!
        val original = repo.observeTemplateItems(baseId).first().first().exerciseId
        repo.startWorkout(thursday.id)

        val undo = repo.swapExercise(thursday.id, original, "agachamento_hack", alsoInPlan = true)!!
        assertTrue(undo.changedBasePlan)
        assertEquals("agachamento_hack", repo.observeTemplateItems(baseId).first().first().exerciseId)
        assertEquals("agachamento_hack", repo.observeTemplateItems(copyId).first().first().exerciseId)
        repo.undoSwap(undo)
        assertEquals(original, repo.observeTemplateItems(baseId).first().first().exerciseId)

        // Exercício que só este dia tem: o plano base não muda, e a tela avisa.
        repo.addExerciseToTemplate(copyId, "cadeira_abdutora")
        val onlyHere = repo.swapExercise(thursday.id, "cadeira_abdutora", "cadeira_adutora", alsoInPlan = true)!!
        assertFalse(onlyHere.changedBasePlan)
    }

    @Test
    fun `exercício próprio é criado, editado e só sai quando não está em uso`() = runBlocking {
        val id = repo.createExercise("  Abdução em pé na polia ", "Polia", listOf(Muscle.GLUTES), listOf(Muscle.GLUTES, Muscle.ADDUCTORS))
        val created = repo.observeExercises().first().first { it.id == id }
        assertEquals("Abdução em pé na polia", created.name)
        assertTrue(created.custom)
        // Músculo principal não se repete como secundário.
        assertEquals(listOf(Muscle.ADDUCTORS), created.secondary)

        repo.updateExercise(id, "Abdução na polia", "Polia", listOf(Muscle.GLUTES), emptyList())
        assertEquals("Abdução na polia", repo.observeExercises().first().first { it.id == id }.name)

        // Exercício do catálogo não é editado nem apagado.
        repo.updateExercise("supino_reto", "Outro nome", "Barra", listOf(Muscle.CHEST), emptyList())
        assertEquals("Supino reto com barra", repo.observeExercises().first().first { it.id == "supino_reto" }.name)
        assertFalse(repo.deleteExercise("prancha"))
        assertTrue(repo.observeExercises().first().any { it.id == "prancha" })

        // Em um treino do plano: não apaga.
        val templateId = db.templates().observeTemplates().first().first().id
        repo.addExerciseToTemplate(templateId, id)
        assertFalse(repo.deleteExercise(id))
        val itemId = repo.observeTemplateItems(templateId).first().first { it.exerciseId == id }.id
        repo.removeTemplateItem(itemId)
        assertTrue(repo.deleteExercise(id))
        assertNull(repo.observeExercises().first().firstOrNull { it.id == id })
    }

    @Test
    fun `todo exercício do catálogo tem GIF e a abdutora e a adutora estão lá`() = runBlocking {
        val ids = repo.observeExercises().first().map { it.id }.toSet()
        assertTrue("cadeira_abdutora" in ids && "cadeira_adutora" in ids)
        assertEquals(SeedData.exercises.map { it.id }.toSet(), ExerciseMedia.gifIds)
        assertEquals(SeedData.exercises.map { it.id }.toSet(), ExerciseMedia.videoIds)
    }
}
