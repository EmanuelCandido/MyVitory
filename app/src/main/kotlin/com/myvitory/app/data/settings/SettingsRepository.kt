package com.myvitory.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    /** Lembrete do treino do dia. Desligado até a pessoa pedir: notificação não pedida vira desinstalação. */
    val todayReminder: Boolean = false,
    /** Minutos desde a meia-noite. */
    val todayReminderMinutes: Int = 17 * 60 + 30,
    /** Às 9h, pergunta se treinou ontem quando o dia ficou sem registro. */
    val yesterdayQuestion: Boolean = false,
    /** Iniciante: +24 h de descanso em cada músculo (recálculo e Recuperação). */
    val beginner: Boolean = false,
) {
    val extraRecoveryHours get() = if (beginner) BEGINNER_EXTRA_HOURS else 0

    companion object {
        const val BEGINNER_EXTRA_HOURS = 24
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val todayReminder = booleanPreferencesKey("today_reminder")
        val todayReminderMinutes = intPreferencesKey("today_reminder_minutes")
        val yesterdayQuestion = booleanPreferencesKey("yesterday_question")
        val beginner = booleanPreferencesKey("beginner")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            todayReminder = p[Keys.todayReminder] ?: defaults.todayReminder,
            todayReminderMinutes = p[Keys.todayReminderMinutes] ?: defaults.todayReminderMinutes,
            yesterdayQuestion = p[Keys.yesterdayQuestion] ?: defaults.yesterdayQuestion,
            beginner = p[Keys.beginner] ?: defaults.beginner,
        )
    }

    suspend fun setTodayReminder(on: Boolean) = context.settingsStore.edit { it[Keys.todayReminder] = on }

    suspend fun setTodayReminderMinutes(minutes: Int) =
        context.settingsStore.edit { it[Keys.todayReminderMinutes] = minutes.coerceIn(0, 24 * 60 - 1) }

    suspend fun setYesterdayQuestion(on: Boolean) = context.settingsStore.edit { it[Keys.yesterdayQuestion] = on }

    suspend fun setBeginner(on: Boolean) = context.settingsStore.edit { it[Keys.beginner] = on }
}
