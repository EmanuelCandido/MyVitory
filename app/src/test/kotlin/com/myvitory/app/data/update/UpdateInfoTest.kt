package com.myvitory.app.data.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** O version.json que web/scripts/publicar-app.mjs gera. */
@RunWith(RobolectricTestRunner::class)
class UpdateInfoTest {
    private val json = """
        {
          "versionCode": 12,
          "versionName": "0.12.0",
          "apk": "myvitory-12.apk",
          "sha256": "ABCDEF",
          "sizeBytes": 4857510,
          "notes": "Observação nos exercícios",
          "publishedAt": "2026-10-31T23:00:00.000Z"
        }
    """.trimIndent()

    @Test
    fun `lê a versão e resolve o endereço do apk ao lado do version json`() {
        val info = UpdateInfo.parse(json, "https://exemplo.web.app/app/version.json")

        assertEquals(12, info.versionCode)
        assertEquals("https://exemplo.web.app/app/myvitory-12.apk", info.apkUrl)
        assertEquals("abcdef", info.sha256)
        assertEquals("Observação nos exercícios", info.notes)
    }

    @Test
    fun `só oferece versão maior que a instalada`() {
        val info = UpdateInfo.parse(json, "https://x.web.app/app/version.json")
        assertTrue(info.isNewerThan(11))
        assertFalse(info.isNewerThan(12))
        assertFalse(info.isNewerThan(13))
    }

    @Test
    fun `sha256 do arquivo baixado`() {
        val file = File.createTempFile("apk", ".bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateInfo.sha256(file))
        file.delete()
    }
}
