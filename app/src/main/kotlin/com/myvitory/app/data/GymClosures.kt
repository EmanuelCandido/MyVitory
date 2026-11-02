package com.myvitory.app.data

import com.myvitory.app.data.db.DatePlanEntity
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.PlanDayEntity
import com.myvitory.app.domain.GymCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Datas entre [from] e [to] em que a academia está fechada, com o motivo (lido do plano e das datas do painel). */
suspend fun MyVitoryDatabase.gymClosed(from: LocalDate, to: LocalDate): Map<LocalDate, String> =
    closedOf(plan().getPlan(), datePlans().getRange(from, to), from, to)

fun MyVitoryDatabase.observeGymClosed(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String>> =
    combine(plan().observePlan(), datePlans().observeRange(from, to)) { days, dates -> closedOf(days, dates, from, to) }

private fun closedOf(days: List<PlanDayEntity>, dates: List<DatePlanEntity>, from: LocalDate, to: LocalDate) =
    GymCalendar.closed(
        days.filter { it.closed }.map { DayOfWeek.of(it.dayOfWeek) }.toSet(),
        dates.filter { it.closed }.associate { it.date to it.label },
        from,
        to,
    )
