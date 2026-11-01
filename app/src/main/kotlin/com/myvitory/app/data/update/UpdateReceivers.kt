package com.myvitory.app.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myvitory.app.BuildConfig
import com.myvitory.app.MyVitoryApp
import com.myvitory.app.notify.Notifications
import java.util.concurrent.TimeUnit

/** Resultado do instalador do Android: pede a confirmação quando precisa e avisa quando deu errado. */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // O app é substituído e fecha; o PackageReplacedReceiver avisa.
            PackageInstaller.STATUS_FAILURE_ABORTED -> updater(context)?.installFailed("Atualização cancelada.")
            else -> updater(context)?.installFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "O Android não instalou: $it" } ?: "O Android não instalou a atualização.",
            )
        }
    }

    private fun updater(context: Context) = (context.applicationContext as MyVitoryApp).container.updater
}

/** Depois de atualizar, o app abre fechado: uma notificação confirma que deu certo. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Notifications.post(
            context,
            Notifications.ID_UPDATE,
            Notifications.base(context, Notifications.CHANNEL_UPDATES)
                .setContentTitle("MyVitory atualizado")
                .setContentText("Versão ${BuildConfig.VERSION_NAME} instalada. Toque para abrir.")
                .setAutoCancel(true)
                .setContentIntent(Notifications.openApp(context, 17)),
        )
    }
}

/** Uma vez por dia procura versão nova e, se houver, avisa com uma notificação. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updater = (applicationContext as MyVitoryApp).container.updater ?: return Result.success()
        val info = updater.check() ?: return Result.success()
        Notifications.post(
            applicationContext,
            Notifications.ID_UPDATE,
            Notifications.base(applicationContext, Notifications.CHANNEL_UPDATES)
                .setContentTitle("Atualização do MyVitory")
                .setContentText("Versão ${info.versionName} disponível. Toque para atualizar.")
                .setAutoCancel(true)
                .setContentIntent(Notifications.openApp(applicationContext, 16)),
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "procurar-atualizacao"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
