package com.myvitory.app.ui.settings

import androidx.lifecycle.ViewModel
import com.myvitory.app.data.settings.AppSettings
import com.myvitory.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Os lembretes se reagendam sozinhos: o app observa os ajustes (MyVitoryApp). */
class SettingsViewModel(private val repo: SettingsRepository, val searchEnabled: Boolean) : ViewModel() {
    val settings: Flow<AppSettings> = repo.settings

    suspend fun setTodayReminder(on: Boolean) = repo.setTodayReminder(on)

    suspend fun setTime(minutes: Int) = repo.setTodayReminderMinutes(minutes)

    suspend fun setYesterdayQuestion(on: Boolean) = repo.setYesterdayQuestion(on)
}
