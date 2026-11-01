package com.myvitory.app.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** O Android ainda não deixou o MyVitory instalar apps: a pessoa libera em Configurações. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Entregue ao instalador do Android (pode aparecer a confirmação; o app fecha ao instalar). */
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
}

/**
 * Atualização sem Play Store e sem cabo: procura o version.json publicado no Hosting do painel, baixa o APK,
 * confere o SHA-256 e instala pelo PackageInstaller. O APK precisa ter a mesma assinatura do app instalado
 * (keystore.properties), senão o Android recusa.
 */
class AppUpdater(
    private val context: Context,
    private val versionUrl: String,
    private val currentVersionCode: Int,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val busy = Mutex()

    val enabled get() = versionUrl.isNotBlank()

    /** Procura versão nova. Sem internet ou sem nada novo, não incomoda (só o botão dos Ajustes mostra o erro). */
    suspend fun check(): UpdateInfo? {
        if (!enabled) return null
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return null
        _state.value = UpdateState.Checking
        return try {
            val info = withContext(Dispatchers.IO) { UpdateInfo.parse(get(versionUrl), versionUrl) }
            _state.value = if (info.isNewerThan(currentVersionCode)) UpdateState.Available(info) else UpdateState.UpToDate
            info.takeIf { it.isNewerThan(currentVersionCode) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(null, "Não deu para procurar atualização. Confira a internet.")
            null
        }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Tela do Android para liberar "instalar apps desconhecidos" para o MyVitory. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun downloadAndInstall(info: UpdateInfo) = busy.withLock {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(info)
            return@withLock
        }
        try {
            val apk = withContext(Dispatchers.IO) { download(info) }
            _state.value = UpdateState.Installing(info)
            withContext(Dispatchers.IO) { install(apk) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(info, e.message ?: "Falha ao atualizar.")
        }
    }

    /** Chamado pelo [UpdateStatusReceiver] quando o Android recusa ou a pessoa cancela. */
    fun installFailed(message: String) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = if (info != null) UpdateState.Failed(info, message) else UpdateState.Failed(null, message)
    }

    private fun download(info: UpdateInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "myvitory-${info.versionCode}.apk")
        val connection = open(info.apkUrl)
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (UpdateInfo.sha256(file) != info.sha256) {
            file.delete()
            error("O arquivo baixado veio diferente do publicado. Tente de novo.")
        }
        return file
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+: quando o próprio MyVitory instalou a versão atual, atualiza sem perguntar.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("myvitory.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, UpdateStatusReceiver::class.java),
                // MUTABLE: o instalador preenche o resultado nos extras.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
    }

    private fun get(url: String): String {
        val connection = open(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            if (responseCode !in 200..299) error("Servidor respondeu $responseCode")
        }
}
