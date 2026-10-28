package com.myvitory.app.domain

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

object ReminderLogic {
    const val YESTERDAY_QUESTION_MINUTES = 9 * 60

    /** Quanto falta até o próximo [minutesOfDay] (hoje, se ainda não passou; senão amanhã). */
    fun delayUntil(now: LocalDateTime, minutesOfDay: Int): Duration {
        val target = now.toLocalDate().atTime(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
        val next = if (target.isAfter(now)) target else target.plusDays(1)
        return Duration.between(now, next)
    }

    fun formatTime(minutesOfDay: Int) = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
}
