package com.myvitory.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myvitory.app.notify.Notifications
import com.myvitory.app.ui.AppLink
import com.myvitory.app.ui.MyVitoryRoot
import com.myvitory.app.ui.theme.MyVitoryTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Destino pedido por uma notificação, consumido pela navegação. */
    private val link = MutableStateFlow<AppLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Recriada (ex.: rotação): o link já foi tratado.
        if (savedInstanceState == null) link.value = parse(intent)
        setContent {
            val pending by link.collectAsStateWithLifecycle()
            MyVitoryTheme {
                MyVitoryRoot(link = pending, onLinkHandled = { link.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        link.value = parse(intent)
    }

    private fun parse(intent: Intent?): AppLink? {
        val sessionId = intent?.getLongExtra(Notifications.EXTRA_SESSION, -1) ?: -1
        return when (intent?.getStringExtra(Notifications.EXTRA_OPEN)) {
            Notifications.OPEN_WORKOUT -> if (sessionId > 0) AppLink.Workout(sessionId) else AppLink.Today
            Notifications.OPEN_MISSED -> if (sessionId > 0) AppLink.Missed(sessionId) else AppLink.Today
            Notifications.OPEN_TODAY -> AppLink.Today
            else -> null
        }
    }
}
