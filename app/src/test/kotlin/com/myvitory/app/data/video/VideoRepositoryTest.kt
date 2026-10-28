package com.myvitory.app.data.video

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.db.MyVitoryDatabase
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoRepositoryTest {
    private val searchJson = """
        {"items":[
          {"id":{"videoId":"AAAAAAAAAA1"},"snippet":{"title":"Leg press &quot;45&quot; &amp; dicas","channelTitle":"Canal A",
            "thumbnails":{"medium":{"url":"https://i.ytimg.com/vi/AAAAAAAAAA1/mqdefault.jpg"}}}},
          {"id":{"videoId":"BBBBBBBBBB2"},"snippet":{"title":"Leg press passo a passo","channelTitle":"Canal B","thumbnails":{}}}
        ]}
    """.trimIndent()
    private val videosJson = """{"items":[{"id":"AAAAAAAAAA1","contentDetails":{"duration":"PT5M40S"}}]}"""

    private val calls = mutableListOf<Pair<String, Map<String, String>>>()
    private var offline = false
    private var quota = false
    private val http = HttpGet { url, headers ->
        calls += url to headers
        when {
            offline -> throw IOException("sem rede")
            quota -> 403 to """{"error":{"errors":[{"reason":"quotaExceeded"}]}}"""
            url.contains("/search") -> 200 to searchJson
            else -> 200 to videosJson
        }
    }

    private var now = 1_000_000L
    private lateinit var db: MyVitoryDatabase
    private lateinit var repo: VideoRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        repo = VideoRepository(db, YouTubeApi("CHAVE", "com.myvitory.app", "AB:CD", http), clock = { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `busca, decodifica e guarda no cache`() = runBlocking {
        val first = repo.search("Leg press 45° execução correta")
        assertNull(first.error)
        assertEquals(listOf("AAAAAAAAAA1", "BBBBBBBBBB2"), first.videos.map { it.videoId })
        assertEquals("Leg press \"45\" & dicas", first.videos[0].title)
        assertEquals(340, first.videos[0].durationSec)
        assertNull(first.videos[1].durationSec)
        assertEquals("com.myvitory.app", calls.first().second["X-Android-Package"])
        assertTrue(calls.first().first.contains("videoEmbeddable=true"))

        // Dentro de 7 dias não gasta cota de novo.
        val callsBefore = calls.size
        now += 6L * 24 * 3_600_000
        repo.search("leg press 45° execução correta")
        assertEquals(callsBefore, calls.size)

        // Depois de 7 dias busca de novo.
        now += 2L * 24 * 3_600_000
        repo.search("leg press 45° execução correta")
        assertTrue(calls.size > callsBefore)
    }

    @Test
    fun `sem rede ou sem cota devolve o cache velho e o erro`() = runBlocking {
        repo.search("remada")
        now += 8L * 24 * 3_600_000
        offline = true
        val offlineLookup = repo.search("remada")
        assertEquals(VideoError.Offline, offlineLookup.error)
        assertEquals(2, offlineLookup.videos.size)

        offline = false
        quota = true
        val quotaLookup = repo.search("remada")
        assertEquals(VideoError.Quota, quotaLookup.error)
        assertEquals(2, quotaLookup.videos.size)
    }

    @Test
    fun `sem chave a busca fica desligada`() = runBlocking {
        val noKey = VideoRepository(db, YouTubeApi("", "com.myvitory.app", null, http))
        val lookup = noKey.search("supino")
        assertEquals(VideoError.NoKey, lookup.error)
        assertTrue(calls.isEmpty())
    }
}
