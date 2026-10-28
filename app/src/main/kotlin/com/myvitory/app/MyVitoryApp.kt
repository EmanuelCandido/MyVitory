package com.myvitory.app

import android.app.Application
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.settings.SettingsRepository
import com.myvitory.app.data.video.AppSignature
import com.myvitory.app.data.video.VideoRepository
import com.myvitory.app.data.video.YouTubeApi
import com.myvitory.app.notify.Notifications
import com.myvitory.app.notify.ReminderWorker
import com.myvitory.app.timer.RestTimerHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** O que os ViewModels recebem. */
class AppContainer(
    val training: TrainingRepository,
    val coach: CoachRepository,
    val videos: VideoRepository,
    val settings: SettingsRepository,
    val restTimer: RestTimerHandle,
)

/**
 * Injeção manual: um banco, repositórios e dois serviços do sistema, sem framework.
 * Continua pequeno o bastante para não justificar Hilt.
 */
class MyVitoryApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())

    val container: AppContainer by lazy {
        val db = MyVitoryDatabase.create(this)
        val api = YouTubeApi(BuildConfig.YOUTUBE_API_KEY, packageName, AppSignature.sha1(this))
        AppContainer(
            training = TrainingRepository(db),
            coach = CoachRepository(db),
            videos = VideoRepository(db, api),
            settings = SettingsRepository(this),
            restTimer = RestTimerHandle(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { container.training.seedIfEmpty() }
        // Reagenda os lembretes a cada abertura e a cada mudança nos ajustes (mantém o horário alinhado).
        appScope.launch {
            container.settings.settings.distinctUntilChanged().collect { ReminderWorker.schedule(this@MyVitoryApp, it) }
        }
    }
}
