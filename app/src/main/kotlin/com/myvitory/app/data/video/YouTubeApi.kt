package com.myvitory.app.data.video

import com.myvitory.app.domain.VideoLinks
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class VideoResult(
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val durationSec: Int?,
)

sealed interface VideoError {
    /** Sem chave configurada: a busca fica desligada, vídeo fixado continua tocando. */
    data object NoKey : VideoError
    data object Offline : VideoError
    data object Quota : VideoError
    data class Http(val code: Int) : VideoError
}

class VideoSearchException(val error: VideoError) : Exception(error.toString())

/** Transporte HTTP trocável nos testes. Devolve (código, corpo). */
fun interface HttpGet {
    fun get(url: String, headers: Map<String, String>): Pair<Int, String>
}

/**
 * Busca na YouTube Data API v3. Custo: search.list = 100 unidades, videos.list = 1 (cota padrão: 10.000/dia).
 * Os cabeçalhos X-Android-* permitem restringir a chave a este app no Google Cloud.
 */
class YouTubeApi(
    private val apiKey: String,
    private val packageName: String,
    private val certSha1: String?,
    private val http: HttpGet = UrlConnectionGet,
) {
    val enabled get() = apiKey.isNotBlank()

    suspend fun search(query: String, max: Int = 8): List<VideoResult> = withContext(Dispatchers.IO) {
        if (!enabled) throw VideoSearchException(VideoError.NoKey)
        val q = URLEncoder.encode(query, "UTF-8")
        val search = call(
            "$BASE/search?part=snippet&type=video&videoEmbeddable=true&safeSearch=moderate" +
                "&relevanceLanguage=pt&regionCode=BR&maxResults=$max&q=$q&key=$apiKey"
        )
        val items = search.getJSONArray("items")
        val found = (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i)
            val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
            if (id.isEmpty()) return@mapNotNull null
            val snippet = item.getJSONObject("snippet")
            VideoResult(
                videoId = id,
                title = decodeEntities(snippet.optString("title")),
                channel = decodeEntities(snippet.optString("channelTitle")),
                thumbnailUrl = snippet.optJSONObject("thumbnails")?.let { t ->
                    (t.optJSONObject("medium") ?: t.optJSONObject("default"))?.optString("url")
                },
                durationSec = null,
            )
        }
        if (found.isEmpty()) return@withContext found
        // Duração custa 1 unidade e ajuda a escolher (vídeo de 40 s x 15 min).
        val durations = runCatching {
            val details = call("$BASE/videos?part=contentDetails&id=${found.joinToString(",") { it.videoId }}&key=$apiKey")
            val list = details.getJSONArray("items")
            (0 until list.length()).associate { i ->
                val v = list.getJSONObject(i)
                v.getString("id") to VideoLinks.parseDuration(v.getJSONObject("contentDetails").optString("duration"))
            }
        }.getOrDefault(emptyMap())
        found.map { it.copy(durationSec = durations[it.videoId]) }
    }

    private fun call(url: String): JSONObject {
        val headers = buildMap {
            put("X-Android-Package", packageName)
            certSha1?.let { put("X-Android-Cert", it) }
        }
        val (code, body) = try {
            http.get(url, headers)
        } catch (e: IOException) {
            throw VideoSearchException(VideoError.Offline)
        }
        if (code == 403 && body.contains("quota", ignoreCase = true)) throw VideoSearchException(VideoError.Quota)
        if (code !in 200..299) throw VideoSearchException(VideoError.Http(code))
        return JSONObject(body)
    }

    companion object {
        private const val BASE = "https://www.googleapis.com/youtube/v3"

        private val entities = mapOf("&quot;" to "\"", "&#39;" to "'", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">")

        /** A API devolve títulos com entidades HTML ("Leg press &quot;45&quot;"). */
        fun decodeEntities(text: String): String = entities.entries.fold(text) { acc, (k, v) -> acc.replace(k, v) }
    }
}

private object UrlConnectionGet : HttpGet {
    override fun get(url: String, headers: Map<String, String>): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}
