package com.myvitory.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.SessionStatus
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
        training.seedIfEmpty()
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
    fun `recuperação usa os treinos feitos`() = runBlocking {
        val muscles = coach.recoveryNow(coach.observeRecoveryEvents().first()).associateBy { it.muscle }
        // Costas: terça às 19h até quinta às 9h = 38 de 72 h.
        assertEquals(52, muscles.getValue(Muscle.BACK).percent)
        assertEquals(100, muscles.getValue(Muscle.QUADS).percent)
        assertTrue(!muscles.getValue(Muscle.CHEST).ready)
    }
}
