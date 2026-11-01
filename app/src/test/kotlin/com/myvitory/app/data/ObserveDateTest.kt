package com.myvitory.app.data

import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDateTest {
    private val sunday = LocalDate.of(2026, 11, 1)

    @Test
    fun `a data vira sozinha e não repete o mesmo dia`() = runTest {
        var date = sunday
        val seen = mutableListOf<LocalDate>()
        val job = launch { observeDate { date }.take(2).toList(seen) }
        runCurrent()
        advanceTimeBy(5 * 60_000) // minutos passam no mesmo dia: nada novo
        assertEquals(listOf(sunday), seen)

        date = sunday.plusDays(1) // meia-noite: segunda, semana nova
        advanceTimeBy(60_001)
        job.join()
        assertEquals(listOf(sunday, sunday.plusDays(1)), seen)
    }
}
