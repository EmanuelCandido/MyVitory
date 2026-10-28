package com.myvitory.app.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.myvitory.app.MyVitoryApp
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.settings.AppSettings
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.ReminderLogic
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** O que um lembrete diz. Separado do Android para dar para testar a decisão. */
data class ReminderContent(val session: SessionEntity, val exerciseCount: Int)

/** Decide se há algo para lembrar. Null: nada a dizer (descanso, já treinou, já marcou). */
class ReminderDecider(private val training: TrainingRepository) {
    suspend fun today(): ReminderContent? {
        val date = training.today()
        training.ensureWeek(WeekLogic.weekStartOf(date))
        val day = training.observeWeek(WeekLogic.weekStartOf(date)).first().first { it.date == date }
        val session = day.session ?: return null
        if (session.status != SessionStatus.PLANNED || session.startedAt != null) return null
        return ReminderContent(session, day.exerciseCount)
    }

    suspend fun yesterday(): ReminderContent? {
        val today = training.today()
        val date = today.minusDays(1)
        val weekStart = WeekLogic.weekStartOf(date)
        // Cria as sessões da semana atual se o app não foi aberto; semana passada só pergunta do que já existia.
        if (weekStart == WeekLogic.weekStartOf(today)) training.ensureWeek(weekStart)
        val day = training.observeWeek(weekStart).first().first { it.date == date }
        if (day.state != DayState.UNMARKED) return null
        return ReminderContent(day.session ?: return null, day.exerciseCount)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MyVitoryApp
        val decider = ReminderDecider(app.container.training)
        when (inputData.getString(KEY_KIND)) {
            KIND_TODAY -> decider.today()?.let { showToday(applicationContext, it) }
            KIND_YESTERDAY -> decider.yesterday()?.let { showYesterday(applicationContext, it) }
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_TODAY = "today"
        const val KIND_YESTERDAY = "yesterday"
        private const val WORK_TODAY = "lembrete-treino-do-dia"
        private const val WORK_YESTERDAY = "pergunta-dia-seguinte"

        /** Agenda (ou cancela) os dois lembretes diários conforme os ajustes. */
        fun schedule(context: Context, settings: AppSettings, now: LocalDateTime = LocalDateTime.now()) {
            val work = WorkManager.getInstance(context)
            if (settings.todayReminder) {
                enqueue(work, WORK_TODAY, KIND_TODAY, ReminderLogic.delayUntil(now, settings.todayReminderMinutes).toMinutes())
            } else {
                work.cancelUniqueWork(WORK_TODAY)
            }
            if (settings.yesterdayQuestion) {
                enqueue(work, WORK_YESTERDAY, KIND_YESTERDAY, ReminderLogic.delayUntil(now, ReminderLogic.YESTERDAY_QUESTION_MINUTES).toMinutes())
            } else {
                work.cancelUniqueWork(WORK_YESTERDAY)
            }
        }

        private fun enqueue(work: WorkManager, name: String, kind: String, delayMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_KIND to kind))
                .build()
            // UPDATE: mudar o horário reaproveita o trabalho existente com o novo atraso.
            work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun showToday(context: Context, content: ReminderContent) {
            val session = content.session
            Notifications.post(
                context,
                Notifications.ID_TODAY,
                Notifications.base(context, Notifications.CHANNEL_REMINDERS)
                    .setContentTitle("Hoje: ${session.name}")
                    .setContentText("${content.exerciseCount} exercícios. Bora?")
                    .setAutoCancel(true)
                    .setContentIntent(Notifications.openApp(context, 10, Notifications.OPEN_TODAY))
                    .addAction(0, "Começar treino", Notifications.openApp(context, 11, Notifications.OPEN_WORKOUT, session.id)),
            )
        }

        fun showYesterday(context: Context, content: ReminderContent) {
            val session = content.session
            Notifications.post(
                context,
                Notifications.ID_YESTERDAY,
                Notifications.base(context, Notifications.CHANNEL_REMINDERS)
                    .setContentTitle("Você treinou ${session.name} ontem?")
                    .setContentText("Com a resposta, a semana se ajusta ao seu descanso.")
                    .setAutoCancel(true)
                    .setContentIntent(Notifications.openApp(context, 12, Notifications.OPEN_TODAY))
                    .addAction(0, "Treinei", MarkDoneReceiver.intent(context, session.id))
                    .addAction(0, "Não treinei", Notifications.openApp(context, 13, Notifications.OPEN_MISSED, session.id))
                    .setStyle(NotificationCompat.BigTextStyle().bigText("Com a resposta, a semana se ajusta ao seu descanso.")),
            )
        }
    }
}

/** "Treinei" direto da notificação: marca como feito sem abrir o app. */
class MarkDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getLongExtra(Notifications.EXTRA_SESSION, -1)
        if (sessionId <= 0) return
        val app = context.applicationContext as MyVitoryApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.training.markSession(sessionId, SessionStatus.DONE)
                Notifications.cancel(context, Notifications.ID_YESTERDAY)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun intent(context: Context, sessionId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context, sessionId.toInt(),
                Intent(context, MarkDoneReceiver::class.java).putExtra(Notifications.EXTRA_SESSION, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
