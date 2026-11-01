package com.myvitory.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.Weighing
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeightRepositoryTest {
    private val today = LocalDate.of(2026, 10, 30)
    private lateinit var db: MyVitoryDatabase
    private lateinit var repo: WeightRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        repo = WeightRepository(db, today = { today })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `registra em qualquer data passada, em ordem, uma por dia`() = runBlocking {
        repo.record(today, 80.04)
        repo.record(today.minusDays(40), 82.0)
        val before = repo.record(today, 79.8)

        assertEquals(Weighing(today, 80.0), before) // arredonda para 0,1 kg
        assertEquals(listOf(Weighing(today.minusDays(40), 82.0), Weighing(today, 79.8)), repo.all())
    }

    @Test
    fun `não aceita data futura nem peso absurdo`() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.record(today.plusDays(1), 80.0) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.record(today, 5.0) } }
    }

    @Test
    fun `desfazer volta o dia como era`() = runBlocking {
        val none = repo.record(today, 80.0)
        assertNull(none)
        repo.restore(today, none)
        assertEquals(emptyList<Weighing>(), repo.all())

        repo.record(today, 80.0)
        val previous = repo.record(today, 81.0)
        repo.restore(today, previous)
        assertEquals(listOf(Weighing(today, 80.0)), repo.all())

        repo.delete(today)
        repo.restore(today, Weighing(today, 80.0))
        assertEquals(listOf(Weighing(today, 80.0)), repo.all())
    }
}
