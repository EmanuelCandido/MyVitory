package com.myvitory.app.domain.sync

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** O formato do Firestore, o mesmo que o painel lê e escreve (web/src/sync/codec.ts usa o mesmo exemplo). */
@RunWith(RobolectricTestRunner::class)
class SnapshotCodecTest {
    private val sample = PlanSnapshot(
        templates = mapOf(
            "f00d" to SnapshotTemplate(
                "A", "Peito + Tríceps", 1_730_000_000_000,
                listOf(SnapshotItem("supino_reto", 4, 8, 120, "Descer em 3 s"), SnapshotItem("custom_abc", 3, 12, 60)),
            ),
        ),
        week = mapOf(1 to SnapshotDay("f00d", 1_730_000_000_001), 2 to SnapshotDay(null, 0), 7 to SnapshotDay(null, 9, closed = true)),
        exercises = mapOf("custom_abc" to SnapshotExercise("Abdução na polia", "Polia", listOf("GLUTES"), listOf("ADDUCTORS"), 5)),
        deleted = mapOf("template:velho" to 42),
        dates = mapOf(
            "2026-11-02" to SnapshotDate(closed = true, label = "Finados", override = false, templateId = null, updatedAt = 10),
            "2026-11-05" to SnapshotDate(closed = false, label = "", override = true, templateId = "f00d", updatedAt = 11),
        ),
    )

    @Test
    fun `ida e volta não perde nada`() {
        assertEquals(sample, SnapshotCodec.decode(SnapshotCodec.encode(sample)))
    }

    @Test
    fun `lê o exemplo compartilhado com o painel`() {
        val json = JSONObject(File("../docs/sync/plano-exemplo.json").readText())
        assertEquals(sample, SnapshotCodec.decode(json.toMap()))
    }

    @Test
    fun `formato mais novo que o app pede atualização`() {
        assertThrows(IllegalArgumentException::class.java) { SnapshotCodec.decode(mapOf("schema" to 99L)) }
    }

    private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> unwrap(get(key)) }

    private fun unwrap(value: Any?): Any? = when (value) {
        is JSONObject -> value.toMap()
        is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
