package com.myvitory.app.ui.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myvitory.app.ui.theme.Palette
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Player oficial do YouTube (IFrame) dentro do app: o vídeo toca aqui, sem abrir o app do YouTube
 * e sem download. Troca de vídeo só "carrega" (cue): nada toca sem o usuário apertar play.
 * O botão de tela cheia do player abre o vídeo em paisagem, sem barras do sistema.
 */
@Composable
fun YouTubePlayerBox(
    videoId: String,
    modifier: Modifier = Modifier,
    onError: (PlayerConstants.PlayerError) -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    val currentVideo by rememberUpdatedState(videoId)
    val currentOnError by rememberUpdatedState(onError)

    // O player entrega a própria view de tela cheia e a função de sair; mostramos por cima de tudo.
    var fullscreen by remember { mutableStateOf<Pair<View, () -> Unit>?>(null) }

    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.Surface)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    lifecycle.addObserver(this)
                    addFullscreenListener(object : FullscreenListener {
                        override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                            fullscreen = fullscreenView to exitFullscreen
                        }

                        override fun onExitFullscreen() {
                            fullscreen = null
                        }
                    })
                    val options = IFramePlayerOptions.Builder(context)
                        .controls(1)
                        .rel(0)
                        .ivLoadPolicy(3)
                        .fullscreen(1)
                        .langPref("pt")
                        .build()
                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                player = youTubePlayer
                                youTubePlayer.cueVideo(currentVideo, 0f)
                            }

                            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                currentOnError(error)
                            }
                        },
                        true,
                        options,
                    )
                }
            },
            onRelease = { view ->
                lifecycle.removeObserver(view)
                view.release()
            },
        )
    }

    LaunchedEffect(videoId, player) {
        player?.cueVideo(videoId, 0f)
    }

    fullscreen?.let { (view, exit) -> FullscreenVideo(view, onExit = exit) }
}

/** Tela cheia: paisagem enquanto aberta, sem barras do sistema; voltar sai da tela cheia. */
@Composable
private fun FullscreenVideo(view: View, onExit: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let {
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            onDispose { }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            factory = { context ->
                FrameLayout(context).apply {
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            onRelease = { container -> container.removeView(view) },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
