package com.myvitory.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import androidx.work.Configuration
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.WeightRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.settings.SettingsRepository
import com.myvitory.app.data.sync.CoachLink
import com.myvitory.app.data.sync.FirebaseSetup
import com.myvitory.app.data.sync.SyncManager
import com.myvitory.app.data.sync.SyncPrefs
import com.myvitory.app.data.update.AppUpdater
import com.myvitory.app.data.update.UpdateCheckWorker
import com.myvitory.app.data.video.AppSignature
import com.myvitory.app.data.video.VideoRepository
import com.myvitory.app.data.video.YouTubeApi
import com.myvitory.app.notify.Notifications
import com.myvitory.app.notify.ReminderWorker
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.timer.RestTimerHandle
import com.myvitory.app.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** O que os ViewModels recebem. */
class AppContainer(
    val training: TrainingRepository,
    val coach: CoachRepository,
    val videos: VideoRepository,
    val settings: SettingsRepository,
    val weights: WeightRepository,
    val restTimer: RestTimerHandle,
    /** Painel do treinador. Null quando o build não tem Firebase configurado. */
    val sync: SyncManager?,
    /** Atualização pelo Hosting do painel. Null quando o build não sabe onde procurar. */
    val updater: AppUpdater?,
)

/**
 * Injeção manual: um banco, repositórios e dois serviços do sistema, sem framework.
 * Continua pequeno o bastante para não justificar Hilt.
 */
class MyVitoryApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob())

    // WorkManager inicializa sob demanda na primeira chamada (o inicializador automático foi removido no manifesto).
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    // GIFs de execução animados (ExerciseDB). O decodificador nativo só existe a partir do Android 9.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(if (Build.VERSION.SDK_INT >= 28) AnimatedImageDecoder.Factory() else GifDecoder.Factory()) }
            .build()

    val container: AppContainer by lazy {
        val db = MyVitoryDatabase.create(this)
        val api = YouTubeApi(BuildConfig.YOUTUBE_API_KEY, packageName, AppSignature.sha1(this))
        val settings = SettingsRepository(this)
        val training = TrainingRepository(db)
        val sync = if (FirebaseSetup.configured) {
            val prefs = SyncPrefs(this)
            SyncManager(this, db, training, prefs, CoachLink(this, db, prefs))
        } else {
            null
        }
        AppContainer(
            training = training,
            coach = CoachRepository(db, extraRecoveryHours = { settings.settings.first().extraRecoveryHours }),
            videos = VideoRepository(db, api),
            settings = settings,
            weights = WeightRepository(db),
            restTimer = RestTimerHandle(this),
            sync = sync,
            updater = AppUpdater(this, BuildConfig.UPDATE_URL, BuildConfig.VERSION_CODE).takeIf { it.enabled },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        appScope.launch { container.training.seedIfEmpty() }
        // Widget acompanha a semana: marcar, começar ou recalcular atualiza a tela inicial. Segue a virada do dia.
        appScope.launch {
            val training = container.training
            training.observeToday()
                .flatMapLatest { training.observeWeek(WeekLogic.weekStartOf(it)) }
                .distinctUntilChanged()
                .collect { TodayWidget.refresh(this@MyVitoryApp) }
        }
        container.sync?.start(appScope)
        // Versão nova: procura a cada abertura e uma vez por dia em segundo plano (com notificação).
        container.updater?.let { updater ->
            appScope.launch { updater.check() }
            UpdateCheckWorker.schedule(this)
        }
        // Reagenda os lembretes a cada abertura e a cada mudança nos ajustes (mantém o horário alinhado).
        appScope.launch {
            container.settings.settings.distinctUntilChanged().collect { ReminderWorker.schedule(this@MyVitoryApp, it) }
        }
    }
}
