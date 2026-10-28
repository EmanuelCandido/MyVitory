package com.myvitory.app.widget

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.SessionStatus
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodayWidgetTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private var today = monday.plusDays(3) // quinta: Ombros + Abdômen
    private lateinit var db: MyVitoryDatabase
    private lateinit var training: TrainingRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        training = TrainingRepository(db, today = { today })
        training.seedIfEmpty()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `dia de treino mostra o treino e o botão começar, depois continuar e feito`() = runBlocking {
        val start = WidgetContentLoader.load(training)
        assertEquals("Ombros + Abdômen", start.title)
        assertEquals("5 exercícios", start.subtitle)
        assertEquals(WidgetAction.START, start.action)

        training.startWorkout(start.sessionId!!)
        assertEquals(WidgetAction.CONTINUE, WidgetContentLoader.load(training).action)

        training.markSession(start.sessionId!!, SessionStatus.DONE)
        val done = WidgetContentLoader.load(training)
        assertEquals("Ombros + Abdômen ✓", done.title)
        assertEquals(WidgetAction.OPEN, done.action)
        assertEquals("Próximo: sexta · Posterior + Glúteos", done.subtitle)
    }

    @Test
    fun `descanso e dia indisponível`() = runBlocking {
        today = monday.plusDays(5) // sábado
        val rest = WidgetContentLoader.load(training)
        assertEquals("Descanso", rest.title)
        assertEquals("Sem mais treinos nesta semana", rest.subtitle)

        today = monday.plusDays(3)
        training.ensureWeek(monday)
        training.setUnavailable(today, true)
        val blocked = WidgetContentLoader.load(training)
        assertEquals(WidgetAction.REPLAN, blocked.action)
        assertTrue(blocked.subtitle.startsWith("Não vai dar hoje"))
    }
}
