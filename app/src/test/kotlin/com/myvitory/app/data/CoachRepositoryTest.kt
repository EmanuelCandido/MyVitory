package com.myvitory.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.planner.Proposal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
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

/** Semana de exemplo: seg e ter feitos, Pernas (qua) não feito, hoje é quinta. */
@RunWith(RobolectricTestRunner::class)
class CoachRepositoryTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private val today = monday.plusDays(3)
    private val nowMillis = today.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private lateinit var db: MyVitoryDatabase
    private lateinit var training: TrainingRepository
    private lateinit var coach: CoachRepository

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        training = TrainingRepository(db, today = { today }, clock = { nowMillis })
        coach = CoachRepository(db, today = { today }, clock = { nowMillis }, zone = ZoneOffset.UTC)
        TrainingRepository(db, today = { monday }).seedIfEmpty()
        training.ensureWeek(monday)
        val week = training.observeWeek(monday).first()
        training.markSession(week[0].session!!.id, SessionStatus.DONE)
        training.markSession(week[1].session!!.id, SessionStatus.DONE)
        training.markSession(week[2].session!!.id, SessionStatus.MISSED)
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun week() = training.observeWeek(monday).first()

    @Test
    fun `sem treino perdido não há recálculo`() = runBlocking {
        val wednesday = week()[2].session!!
        training.markSession(wednesday.id, SessionStatus.DONE)
        assertNull(coach.buildReplan())
    }

    @Test
    fun `recalcula, aplica e desfaz`() = runBlocking {
        val data = coach.buildReplan()
        assertNotNull(data)
        val best = data!!.result.best
        val legs = data.input.pending.first { it.missed }
        assertEquals(today, best.dateOf(legs))
        assertEquals(1, coach.observePendingMissed().first().size)

        val undo = coach.apply(data, best)
        val after = week()
        assertEquals("Pernas", after[3].session?.name)
        assertEquals(today, after[2].session?.movedTo)
        assertTrue(coach.observePendingMissed().first().isEmpty())
        assertEquals("Ombros + Abdômen", after[4].session?.name)
        assertEquals("Posterior + Glúteos", after[6].session?.name)

        coach.undo(undo)
        val restored = week()
        assertEquals("Ombros + Abdômen", restored[3].session?.name)
        assertNull(restored[2].session?.movedTo)
        assertEquals(1, coach.observePendingMissed().first().size)
    }

    @Test
    fun `descanso decidido não é recriado pelo plano base`() = runBlocking {
        val data = coach.buildReplan()!!
        // Sexta tem treino no plano base; uma proposta que a deixa livre precisa gravar o descanso.
        val restDay = today.plusDays(1)
        coach.apply(data, data.result.best.copy(assignment = data.result.best.assignment.filterKeys { it != restDay }))
        training.ensureWeek(monday)
        assertNull(week().first { it.date == restDay }.session)
    }

    @Test
    fun `dia indisponível fica fora do recálculo`() = runBlocking {
        val sunday = monday.plusDays(6)
        training.setUnavailable(sunday, true)
        val data = coach.buildReplan()!!
        assertTrue(data.input.slots.none { it.date == sunday })
        assertTrue(data.result.best.assignment.keys.none { it == sunday })
        assertEquals(DayState.UNAVAILABLE, week()[6].state)
    }

    @Test
    fun `indisponível num dia de treino manda o treino para o recálculo`() = runBlocking {
        val friday = today.plusDays(1)
        val undo = training.setUnavailable(friday, true)
        assertEquals(SessionStatus.MISSED, week()[4].session?.status)
        assertEquals(2, coach.observePendingMissed().first().size)
        val data = coach.buildReplan()!!
        assertTrue(data.input.slots.none { it.date == friday })

        training.undoAvailability(undo)
        assertEquals(SessionStatus.PLANNED, week()[4].session?.status)
        assertEquals(DayState.PLANNED, week()[4].state)
    }

    @Test
    fun `sem fim de semana, aplica treino fundido com séries reduzidas`() = runBlocking {
        training.setUnavailable(monday.plusDays(5), true)
        training.setUnavailable(monday.plusDays(6), true)
        val data = coach.buildReplan()!!
        val best = data.result.best
        assertTrue(best.dropped.isEmpty())
        val mergedDate = best.assignment.entries.single { it.value.mergedWith != null }.key

        coach.apply(data, best)
        val day = week().first { it.date == mergedDate }
        val session = day.session!!
        assertEquals("Pernas + Posterior + Glúteos", session.name)
        assertNotNull(session.mergedTemplateId)
        assertEquals(mergedDate, week()[2].session?.movedTo)
        assertEquals(9, day.exerciseCount) // 5 + 5, mesa flexora nos dois conta uma vez

        training.startWorkout(session.id)
        val workout = training.observeWorkout(session.id).first()!!
        assertEquals(9, workout.exercises.size)
        assertEquals(1, workout.exercises.count { it.exerciseId == "mesa_flexora" })
        // Uma série a menos em cada exercício, nunca abaixo de 2: stiff 4 → 3, cadeira abdutora 3 → 2.
        assertEquals(3, workout.exercises.first { it.exerciseId == "stiff" }.sets.size)
        assertEquals(2, workout.exercises.first { it.exerciseId == "cadeira_abdutora" }.sets.size)
    }

    @Test
    fun `mudar o plano base ou renomear um treino não desfaz o recálculo`() = runBlocking {
        val data = coach.buildReplan()!!
        coach.apply(data, data.result.best)
        val legsId = week()[3].session!!.templateId!!

        training.renameTemplate(legsId, "Pernas A")
        training.setPlanDay(DayOfWeek.MONDAY, null) // refaz os dias que ninguém começou
        val after = week()
        assertEquals("Pernas A", after[3].session?.name)
        assertEquals(legsId, after[3].session?.templateId)
        assertTrue(after[3].special)
        assertEquals("Ombros + Abdômen", after[4].session?.name)
        assertEquals("Posterior + Glúteos", after[6].session?.name)
        assertEquals(today, after[2].session?.movedTo)
        assertTrue(coach.observePendingMissed().first().isEmpty())
    }

    @Test
    fun `voltar ao plano base num dia recalculado reabre o treino perdido`() = runBlocking {
        val data = coach.buildReplan()!!
        coach.apply(data, data.result.best)

        assertTrue(training.resetDayToPlan(today))
        assertEquals("Ombros + Abdômen", week()[3].session?.name)
        assertNull(week()[2].session?.movedTo)
        assertEquals(1, coach.observePendingMissed().first().size)
    }

    @Test
    fun `segundo recálculo mantém a fusão do primeiro`() = runBlocking {
        training.setUnavailable(monday.plusDays(5), true)
        training.setUnavailable(monday.plusDays(6), true)
        val first = coach.buildReplan()!!
        coach.apply(first, first.result.best)
        val mergedDate = first.result.best.assignment.entries.single { it.value.mergedWith != null }.key
        val merged = week().first { it.date == mergedDate }.session!!

        // Falta mais um dia na mesma semana.
        val other = week().first { it.date != mergedDate && it.date >= today && it.session?.status == SessionStatus.PLANNED }
        training.markSession(other.session!!.id, SessionStatus.MISSED)
        val second = coach.buildReplan()!!
        val kept = second.input.pending.single { it.sessionId == merged.id }
        assertEquals(merged.mergedTemplateId, kept.keptMergeTemplateId)
        assertEquals(10, kept.exerciseCount)
        coach.apply(second, second.result.best)

        val newDate = second.result.best.dateOf(kept)
        assertNotNull(newDate)
        val day = week().first { it.date == newDate }
        assertEquals("Pernas + Posterior + Glúteos", day.session?.name)
        assertEquals(merged.mergedTemplateId, day.session?.mergedTemplateId)
        assertEquals(9, day.exerciseCount)
        assertEquals(newDate, week()[2].session?.movedTo)
    }

    @Test
    fun `perdido de um recálculo anterior acompanha o treino quando ele muda de dia`() = runBlocking {
        val first = coach.buildReplan()!!
        coach.apply(first, first.result.best) // Pernas de quarta foi para hoje (quinta)
        val friday = today.plusDays(1)
        training.setUnavailable(friday, true)

        val second = coach.buildReplan()!!
        val saturday = today.plusDays(2)
        val sunday = today.plusDays(3)
        val legs = second.input.pending.single { !it.missed && it.originalDate == today }
        val shoulders = second.input.pending.single { it.missed }
        val posterior = second.input.pending.single { !it.missed && it.originalDate == sunday }
        // Ombros (perdido na sexta) fica com hoje e empurra Pernas para sábado.
        val proposal = Proposal(mapOf(today to shoulders, saturday to legs, sunday to posterior), emptyList(), 0.0)
        val undo = coach.apply(second, proposal)
        assertEquals(saturday, week()[2].session?.movedTo)
        assertEquals("Pernas", week()[5].session?.name)

        coach.undo(undo)
        assertEquals(today, week()[2].session?.movedTo)
    }

    @Test
    fun `exercício próprio criado depois conta na recuperação`() = runBlocking {
        coach.observeRecoveryEvents().first() // o catálogo já foi lido uma vez
        val thursday = week()[3].session!!
        val id = training.createExercise("Adução na polia", "Polia", listOf(Muscle.ADDUCTORS), emptyList())
        training.addExerciseToTemplate(thursday.templateId!!, id)
        assertEquals(1.0, coach.observeTodayLoads().first()!!.loads[Muscle.ADDUCTORS])

        training.markSession(thursday.id, SessionStatus.DONE)
        val muscles = coach.recoveryNow(coach.observeRecoveryEvents().first()).associateBy { it.muscle }
        assertFalse(muscles.getValue(Muscle.ADDUCTORS).ready)
    }

    @Test
    fun `nível iniciante soma 24 h nas janelas`() = runBlocking {
        val beginner = CoachRepository(db, today = { today }, clock = { nowMillis }, zone = ZoneOffset.UTC, extraRecoveryHours = { 24 })
        val data = beginner.buildReplan()!!
        assertEquals(96, data.input.recoveryHours(Muscle.QUADS))
        val back = beginner.recoveryNow(beginner.observeRecoveryEvents().first(), extraHours = 24).first { it.muscle == Muscle.BACK }
        assertEquals(39, back.percent) // 38 de 96 h
    }

    @Test
    fun `pular encerra a pendência`() = runBlocking {
        val data = coach.buildReplan()!!
        val undo = coach.dismiss(data.missed)
        assertTrue(coach.observePendingMissed().first().isEmpty())
        assertEquals("Ombros + Abdômen", week()[3].session?.name)
        coach.undo(undo)
        assertFalse(coach.observePendingMissed().first().isEmpty())
    }

    @Test
    fun `treino de ontem registrado hoje conta no horário de ontem`() = runBlocking {
        val wednesday = week()[2].session!!
        training.markSession(wednesday.id, SessionStatus.PLANNED) // "Fiz parte" depois de ter marcado perdido
        training.startWorkout(wednesday.id)
        training.observeWorkout(wednesday.id).first()!!.exercises.flatMap { it.sets }.forEach { training.updateSet(it.copy(done = true)) }
        assertEquals(SessionStatus.DONE, training.finishWorkout(wednesday.id))

        val quads = coach.recoveryNow(coach.observeRecoveryEvents().first()).first { it.muscle == Muscle.QUADS }
        // Quarta às 19h até quinta às 9h = 14 de 72 h (e não 0%, como se tivesse treinado agora).
        assertEquals(19, quads.percent)
    }

    @Test
    fun `recuperação usa os treinos feitos`() = runBlocking {
        val muscles = coach.recoveryNow(coach.observeRecoveryEvents().first()).associateBy { it.muscle }
        // Costas: terça às 19h até quinta às 9h = 38 de 72 h.
        assertEquals(52, muscles.getValue(Muscle.BACK).percent)
        assertEquals(100, muscles.getValue(Muscle.QUADS).percent)
        assertTrue(!muscles.getValue(Muscle.CHEST).ready)
    }
}
