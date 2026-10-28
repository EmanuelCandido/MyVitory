package com.myvitory.app.ui.components

import com.myvitory.app.domain.DayState
import java.time.DayOfWeek
import java.time.LocalDate

private val shortDays = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
private val longDays = listOf("Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo")
private val letters = listOf("S", "T", "Q", "Q", "S", "S", "D")
private val months = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

fun DayOfWeek.short() = shortDays[value - 1]
fun DayOfWeek.long() = longDays[value - 1]
fun DayOfWeek.letter() = letters[value - 1]

/** "QUINTA · 29 OUT" */
fun LocalDate.headerLabel() = "${dayOfWeek.long()} · $dayOfMonth ${months[monthValue - 1]}".uppercase()

/** "26 out – 1 nov" */
fun weekRangeLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    return "${start.dayOfMonth} ${months[start.monthValue - 1]} – ${end.dayOfMonth} ${months[end.monthValue - 1]}"
}

fun Int.twoDigits() = toString().padStart(2, '0')

fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "$h:${m.toInt().twoDigits()}:${sec.toInt().twoDigits()}" else "$m:${sec.toInt().twoDigits()}"
}

fun DayState.label(): String? = when (this) {
    DayState.DONE -> "Feito"
    DayState.PARTIAL -> "Parcial"
    DayState.MISSED -> "Não feito"
    DayState.MOVED -> "Movido"
    DayState.UNMARKED -> "Sem registro"
    DayState.TODAY -> "Hoje"
    DayState.PLANNED, DayState.REST -> null
}
