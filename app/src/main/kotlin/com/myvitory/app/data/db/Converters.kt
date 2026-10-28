package com.myvitory.app.data.db

import androidx.room.TypeConverter
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.SessionStatus
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun musclesToString(muscles: List<Muscle>): String = muscles.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscles(value: String): List<Muscle> =
        if (value.isBlank()) emptyList() else value.split(",").map { Muscle.valueOf(it) }

    @TypeConverter
    fun statusToString(status: SessionStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): SessionStatus = SessionStatus.valueOf(value)
}
