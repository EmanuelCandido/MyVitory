package com.myvitory.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myvitory.app.MyVitoryApp
import com.myvitory.app.notify.Notifications
import java.util.concurrent.TimeUnit

/** Sincroniza com o painel do treinador em segundo plano (a cada 15 min e logo depois de uma mudança). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MyVitoryApp
        val sync = app.container.sync ?: return Result.success()
        return when (sync.runOnce()) {
            SyncOutcome.DONE, SyncOutcome.OFF -> Result.success()
            SyncOutcome.FAILED -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "sync-painel"
        private const val NOW = "sync-painel-agora"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Roda assim que houver rede. Se já houver um rodando, este vem logo depois (pega as mudanças do meio). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).run {
                cancelUniqueWork(PERIODIC)
                cancelUniqueWork(NOW)
            }
        }

        /** "Seu treino foi atualizado": só quando o que chegou da nuvem mudou algo no celular. */
        fun notifyChanges(context: Context, result: ImportResult) {
            val parts = buildList {
                if (result.changedTemplates.isNotEmpty()) add(result.changedTemplates.joinToString(", "))
                if (result.deletedTemplates.isNotEmpty()) add("removido: ${result.deletedTemplates.joinToString(", ")}")
                if (result.weekChanged) add("dias da semana")
                if (result.datesChanged > 0) add("datas do calendário")
            }
            if (parts.isEmpty()) return
            Notifications.post(
                context,
                Notifications.ID_COACH,
                Notifications.base(context, Notifications.CHANNEL_COACH)
                    .setContentTitle("Seu treino foi atualizado")
                    .setContentText(parts.joinToString(" · ").replaceFirstChar { it.uppercase() })
                    .setAutoCancel(true)
                    .setContentIntent(Notifications.openApp(context, 15, Notifications.OPEN_TODAY)),
            )
        }
    }
}
