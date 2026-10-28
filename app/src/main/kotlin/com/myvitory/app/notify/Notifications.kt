package com.myvitory.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.myvitory.app.MainActivity
import com.myvitory.app.R

/** Canais, IDs e atalhos comuns das notificações do app. */
object Notifications {
    const val CHANNEL_REMINDERS = "lembretes"
    const val CHANNEL_REST = "descanso"
    const val CHANNEL_REST_DONE = "descanso_fim"

    const val ID_TODAY = 1001
    const val ID_YESTERDAY = 1002
    const val ID_REST = 2001
    const val ID_REST_DONE = 2002

    const val EXTRA_OPEN = "open"
    const val EXTRA_SESSION = "sessionId"
    const val OPEN_WORKOUT = "workout"
    const val OPEN_MISSED = "missed"
    const val OPEN_TODAY = "today"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Lembretes de treino", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Treino do dia e a pergunta se você treinou ontem."
                },
                NotificationChannel(CHANNEL_REST, "Descanso em andamento", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Contagem do descanso entre séries."
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_REST_DONE, "Fim do descanso", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisa quando é hora da próxima série."
                    enableVibration(true)
                    vibrationPattern = REST_DONE_PATTERN
                },
            )
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.lime))

    /** Abre o app; com [open] e [sessionId], leva direto ao lugar certo. */
    fun openApp(context: Context, requestCode: Int, open: String = OPEN_TODAY, sessionId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, open)
        sessionId?.let { intent.putExtra(EXTRA_SESSION, it) }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Duas vibrações curtas: "acabou o descanso". */
    val REST_DONE_PATTERN = longArrayOf(0, 250, 150, 250)

    fun vibrateRestDone(context: Context) {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(REST_DONE_PATTERN, -1))
    }
}
