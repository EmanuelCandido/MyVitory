package com.myvitory.app.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myvitory.app.ui.theme.Palette
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Player oficial do YouTube (IFrame) dentro do app: o vídeo toca aqui, sem abrir o app do YouTube
 * e sem download. Troca de vídeo só "carrega" (cue): nada toca sem o usuário apertar play.
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

    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Palette.Surface)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                YouTubePlayerView(context).apply {
                    enableAutomaticInitialization = false
                    lifecycle.addObserver(this)
                    val options = IFramePlayerOptions.Builder(context)
                        .controls(1)
                        .rel(0)
                        .ivLoadPolicy(3)
                        .fullscreen(0)
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
}
