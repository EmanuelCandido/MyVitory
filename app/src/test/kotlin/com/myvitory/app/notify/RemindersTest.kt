package com.myvitory.app.notify

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.ReminderLogic
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.timer.RestTimer
import com.myvitory.app.timer.RestTimerController
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
class RemindersTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private var today = monday.plusDays(3) // quinta: Ombros + Abdômen
    private lateinit var db: MyVitoryDatabase
    private lateinit var training: TrainingRepository
    private lateinit var decider: ReminderDecider

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        training = TrainingRepository(db, today = { today })
        decider = ReminderDecider(training)
        training.seedIfEmpty()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `horário do próximo lembrete`() {
        val morning = LocalDateTime.of(2026, 10, 29, 8, 0)
        assertEquals(Duration.ofMinutes(9 * 60 + 30), ReminderLogic.delayUntil(morning, 17 * 60 + 30))
        val night = LocalDateTime.of(2026, 10, 29, 20, 0)
        assertEquals(Duration.ofMinutes(21 * 60 + 30), ReminderLogic.delayUntil(night, 17 * 60 + 30))
        assertEquals("07:05", ReminderLogic.formatTime(7 * 60 + 5))
    }

    @Test
    fun `lembra o treino do dia só se ainda não começou`() = runBlocking {
        val content = decider.today()
        assertNotNull(content)
        assertEquals("Ombros + Abdômen", content!!.session.name)
        assertEquals(5, content.exerciseCount)

        training.startWorkout(content.session.id)
        assertNull(decider.today())
    }

    @Test
    fun `dia de descanso não tem lembrete`() = runBlocking {
        today = monday.plusDays(5) // sábado
        assertNull(decider.today())
    }

    @Test
    fun `pergunta de ontem só quando ficou sem registro`() = runBlocking {
        val question = decider.yesterday()
        assertEquals("Pernas", question?.session?.name)

        training.markSession(question!!.session.id, SessionStatus.DONE)
        assertNull(decider.yesterday())
        assertEquals(SessionStatus.DONE, training.observeWeek(monday).first()[2].session?.status)
    }

    @Test
    fun `descanso termina uma vez só, mesmo com tela e serviço disputando`() {
        val timer = RestTimer(endsAt = 10_000, totalSec = 60, label = "Próxima: Leg press")
        RestTimerController.set(timer)
        RestTimerController.adjust(15, now = 0)
        val adjusted = RestTimerController.state.value!!
        assertEquals(25_000, adjusted.endsAt)
        assertFalse("o descanso antigo já foi trocado", RestTimerController.complete(timer))
        assertTrue(RestTimerController.complete(adjusted))
        assertFalse(RestTimerController.complete(adjusted))
        assertNull(RestTimerController.state.value)

        RestTimerController.set(timer)
        RestTimerController.adjust(-15, now = 9_000)
        assertNull("menos 15 s depois do fim encerra o descanso", RestTimerController.state.value)
    }
}
