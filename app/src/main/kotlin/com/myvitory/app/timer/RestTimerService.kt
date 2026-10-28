package com.myvitory.app.timer

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.myvitory.app.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Mantém o descanso vivo com a tela bloqueada: notificação com contagem regressiva, botões +15 s e Pular,
 * e vibração no fim. Tipo "shortService" (Android 14+): até ~3 min por descanso, sem permissão especial.
 * Se o sistema encerrar antes, a contagem continua na tela do treino.
 */
class RestTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLUS -> RestTimerController.adjust(15)
            ACTION_SKIP -> RestTimerController.skip()
        }
        val timer = RestTimerController.state.value
        if (timer == null) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        ServiceCompat.startForeground(this, Notifications.ID_REST, notification(timer), type)
        if (!watching) {
            watching = true
            scope.launch { watch() }
        }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        RestTimerController.state.collectLatest { timer ->
            if (timer == null) {
                stopSelfSafely()
                return@collectLatest
            }
            Notifications.post(this, Notifications.ID_REST, notificationBuilder(timer))
            delay((timer.endsAt - System.currentTimeMillis()).coerceAtLeast(0))
            if (RestTimerController.complete(timer)) RestTimerHandle.restFinished(this)
        }
    }

    // Android 14: o tempo do shortService acabou. Sai já; a tela do treino segue contando.
    override fun onTimeout(startId: Int) = stopSelfSafely()

    override fun onTimeout(startId: Int, fgsType: Int) = stopSelfSafely()

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(timer: RestTimer) = notificationBuilder(timer).build()

    private fun notificationBuilder(timer: RestTimer): NotificationCompat.Builder =
        Notifications.base(this, Notifications.CHANNEL_REST)
            .setContentTitle("Descanso")
            .setContentText(timer.label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(timer.endsAt)
            .setShowWhen(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Notifications.openApp(this, 21))
            .addAction(0, "+15 s", action(this, ACTION_PLUS, 22))
            .addAction(0, "Pular", action(this, ACTION_SKIP, 23))

    companion object {
        private const val ACTION_PLUS = "com.myvitory.app.REST_PLUS"
        private const val ACTION_SKIP = "com.myvitory.app.REST_SKIP"

        private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getService(
                context, requestCode,
                Intent(context, RestTimerService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
