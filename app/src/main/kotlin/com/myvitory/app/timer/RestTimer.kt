package com.myvitory.app.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.myvitory.app.notify.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Descanso em andamento: termina em [endsAt] (relógio do sistema). */
data class RestTimer(val endsAt: Long, val totalSec: Int, val label: String)

/**
 * Fonte única do descanso, compartilhada pela tela do treino e pelo [RestTimerService].
 * Quem chegar primeiro ao fim (tela ou serviço) chama [complete]; só esse avisa o usuário.
 */
object RestTimerController {
    private val _state = MutableStateFlow<RestTimer?>(null)
    val state: StateFlow<RestTimer?> = _state.asStateFlow()

    fun set(timer: RestTimer?) {
        _state.value = timer
    }

    fun adjust(deltaSec: Int, now: Long = System.currentTimeMillis()) {
        val current = _state.value ?: return
        val endsAt = current.endsAt + deltaSec * 1000L
        _state.value = if (endsAt <= now) null else current.copy(endsAt = endsAt, totalSec = (current.totalSec + deltaSec).coerceAtLeast(1))
    }

    fun skip() {
        _state.value = null
    }

    /** Encerra [expected] se ainda for o descanso atual. True só para quem encerrou. */
    fun complete(expected: RestTimer): Boolean = _state.compareAndSet(expected, null)
}

/** O que o ViewModel do treino usa: começa o descanso e liga o serviço que segura a tela bloqueada. */
class RestTimerHandle(private val context: Context) {
    val state get() = RestTimerController.state

    fun start(restSec: Int, label: String, now: Long = System.currentTimeMillis()) {
        RestTimerController.set(RestTimer(now + restSec * 1000L, restSec, label))
        runCatching { ContextCompat.startForegroundService(context, Intent(context, RestTimerService::class.java)) }
    }

    fun adjust(deltaSec: Int) = RestTimerController.adjust(deltaSec)

    fun skip() = RestTimerController.skip()

    fun complete(timer: RestTimer) {
        if (RestTimerController.complete(timer)) restFinished(context)
    }

    companion object {
        /** Vibra sempre; com o app fora da tela, também mostra a notificação "hora da próxima série". */
        fun restFinished(context: Context) {
            Notifications.vibrateRestDone(context)
            val visible = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!visible) {
                Notifications.post(
                    context,
                    Notifications.ID_REST_DONE,
                    Notifications.base(context, Notifications.CHANNEL_REST_DONE)
                        .setContentTitle("Descanso acabou")
                        .setContentText("Hora da próxima série.")
                        .setAutoCancel(true)
                        .setTimeoutAfter(60_000)
                        .setContentIntent(Notifications.openApp(context, 20)),
                )
            }
        }
    }
}
