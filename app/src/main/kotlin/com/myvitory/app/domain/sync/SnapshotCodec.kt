package com.myvitory.app.domain.sync

/**
 * Converte o plano de/para o documento do Firestore (mapas e listas simples, sem reflexão: não depende de
 * regras do R8). O mesmo formato é lido e escrito pelo painel (web/src/sync/codec.ts) e está descrito em
 * docs/painel-web.md. Números chegam como Long do Firestore e como Int/Double de JSON: tudo passa por Number.
 */
object SnapshotCodec {

    fun encode(snapshot: PlanSnapshot): Map<String, Any?> = mapOf(
        "schema" to PlanSnapshot.SCHEMA.toLong(),
        "templates" to snapshot.templates.mapValues { (_, t) ->
            mapOf(
                "code" to t.code,
                "name" to t.name,
                "updatedAt" to t.updatedAt,
                "items" to t.items.map { i ->
                    mapOf(
                        "exerciseId" to i.exerciseId,
                        "sets" to i.sets.toLong(),
                        "reps" to i.reps.toLong(),
                        "restSec" to i.restSec.toLong(),
                        "note" to i.note,
                    )
                },
            )
        },
        "week" to snapshot.week.entries.associate { (day, d) -> day.toString() to mapOf("templateId" to d.templateId, "updatedAt" to d.updatedAt) },
        "exercises" to snapshot.exercises.mapValues { (_, e) ->
            mapOf(
                "name" to e.name,
                "equipment" to e.equipment,
                "primary" to e.primary,
                "secondary" to e.secondary,
                "updatedAt" to e.updatedAt,
            )
        },
        "deleted" to snapshot.deleted,
    )

    /** Lê o documento. Campos faltando viram vazios; versão de formato mais nova que a do app dá erro. */
    fun decode(doc: Map<String, Any?>): PlanSnapshot {
        val schema = (doc["schema"] as? Number)?.toInt() ?: PlanSnapshot.SCHEMA
        require(schema <= PlanSnapshot.SCHEMA) { "Plano no formato $schema: atualize o app." }
        return PlanSnapshot(
            templates = doc.map("templates").mapValues { (_, value) ->
                val t = value.asMap()
                SnapshotTemplate(
                    code = t.string("code"),
                    name = t.string("name"),
                    updatedAt = t.long("updatedAt"),
                    items = (t["items"] as? List<*>).orEmpty().map { raw ->
                        val i = raw.asMap()
                        SnapshotItem(
                            exerciseId = i.string("exerciseId"),
                            sets = i.long("sets").toInt(),
                            reps = i.long("reps").toInt(),
                            restSec = i.long("restSec").toInt(),
                            note = i.string("note"),
                        )
                    },
                )
            },
            week = doc.map("week").entries.mapNotNull { (key, value) ->
                val day = key.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
                val d = value.asMap()
                day to SnapshotDay(d["templateId"] as? String, d.long("updatedAt"))
            }.toMap(),
            exercises = doc.map("exercises").mapValues { (_, value) ->
                val e = value.asMap()
                SnapshotExercise(
                    name = e.string("name"),
                    equipment = e.string("equipment"),
                    primary = e.strings("primary"),
                    secondary = e.strings("secondary"),
                    updatedAt = e.long("updatedAt"),
                )
            },
            deleted = doc.map("deleted").mapValues { (_, value) -> (value as? Number)?.toLong() ?: 0L },
        )
    }

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key].asMap()

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asMap(): Map<String, Any?> = (this as? Map<String, Any?>).orEmpty()

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: ""

    private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L

    private fun Map<String, Any?>.strings(key: String): List<String> = (this[key] as? List<*>).orEmpty().filterIsInstance<String>()
}
