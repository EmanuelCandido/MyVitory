package com.myvitory.app.data

import com.myvitory.app.domain.Muscle
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O painel web usa o mesmo catálogo do app (nomes, músculos, GIF, vídeo e pontos-chave), gerado daqui para
 * web/src/data/catalog.json. Se o catálogo mudar, este teste falha até gerar de novo:
 *
 *     UPDATE_CATALOG=1 ./gradlew :app:testDebugUnitTest --tests '*CatalogExportTest*'
 */
@RunWith(RobolectricTestRunner::class)
class CatalogExportTest {
    private val file = File("../web/src/data/catalog.json")

    @Test
    fun `catálogo do painel igual ao do app`() {
        val generated = catalogJson()
        if (System.getenv("UPDATE_CATALOG") == "1" || !file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(generated)
        }
        assertEquals("Catálogo do painel desatualizado: rode com UPDATE_CATALOG=1", generated, file.readText())
    }

    private fun catalogJson(): String {
        val muscles = JSONArray()
        Muscle.entries.forEach { m -> muscles.put(JSONObject().put("id", m.name).put("label", m.label).put("recoveryHours", m.recoveryHours)) }
        val exercises = JSONArray()
        SeedData.exercises.sortedBy { it.name }.forEach { e ->
            val video = ExerciseMedia.video(e.id)
            exercises.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("equipment", e.equipment)
                    .put("primary", JSONArray(e.primary.map { it.name }))
                    .put("secondary", JSONArray(e.secondary.map { it.name }))
                    .put("gif", ExerciseMedia.gifUrl(e.id) ?: JSONObject.NULL)
                    .put("videoId", video?.videoId ?: JSONObject.NULL)
                    .put("tips", JSONArray(ExerciseTips.forExercise(e.id))),
            )
        }
        // Divisão de exemplo do app (ABCDE): o painel oferece como modelo pronto.
        val templates = JSONArray()
        SeedData.templates.forEach { t ->
            val items = JSONArray()
            t.items.forEach { items.put(JSONObject().put("exerciseId", it.exerciseId).put("sets", it.sets).put("reps", it.reps).put("restSec", it.restSec)) }
            templates.put(JSONObject().put("code", t.code).put("name", t.name).put("day", t.day.value).put("items", items))
        }
        // Barras sem escape ("\/") deixam o arquivo legível; o JSON continua o mesmo.
        return JSONObject().put("muscles", muscles).put("exercises", exercises).put("templates", templates)
            .toString(2).replace("\\/", "/") + "\n"
    }
}
