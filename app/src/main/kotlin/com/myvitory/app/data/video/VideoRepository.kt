package com.myvitory.app.data.video

import com.myvitory.app.data.db.CachedVideoEntity
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.PinnedVideoEntity
import kotlinx.coroutines.flow.Flow

/** Resultado de uma busca: os vídeos (do cache ou da rede) e o erro, se houve. */
data class VideoLookup(val videos: List<CachedVideoEntity>, val error: VideoError?)

/**
 * Vídeos de execução. Ordem de preferência: vídeo fixado pelo usuário, cache de até 7 dias, busca na API.
 * Busca nova só quando o cache expirou ou a pessoa pede "mais vídeos": cada busca custa 1% da cota diária.
 */
class VideoRepository(
    db: MyVitoryDatabase,
    private val api: YouTubeApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.videos()

    val searchEnabled get() = api.enabled

    fun observePinned(exerciseId: String): Flow<PinnedVideoEntity?> = dao.observePinned(exerciseId)

    suspend fun pin(exerciseId: String, videoId: String, title: String, channel: String) =
        dao.pin(PinnedVideoEntity(exerciseId, videoId, title, channel))

    suspend fun unpin(exerciseId: String) = dao.unpin(exerciseId)

    suspend fun search(query: String, forceRefresh: Boolean = false): VideoLookup {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return VideoLookup(emptyList(), null)
        val cached = dao.cached(key)
        val fresh = cached.isNotEmpty() && clock() - cached.first().fetchedAt < CACHE_TTL_MS
        if (fresh && !forceRefresh) return VideoLookup(cached, null)
        return try {
            val now = clock()
            val results = api.search(key).mapIndexed { i, v ->
                CachedVideoEntity(
                    query = key, position = i, videoId = v.videoId, title = v.title, channel = v.channel,
                    thumbnailUrl = v.thumbnailUrl, durationSec = v.durationSec, fetchedAt = now,
                )
            }
            dao.deleteQuery(key)
            dao.insertAll(results)
            VideoLookup(dao.cached(key), null)
        } catch (e: VideoSearchException) {
            // Sem rede ou sem cota: o cache velho ainda serve.
            VideoLookup(cached, e.error)
        }
    }

    companion object {
        const val CACHE_TTL_MS = 7L * 24 * 3_600_000
    }
}
