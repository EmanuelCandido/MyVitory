package com.myvitory.app.data.sync

import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.gymClosed
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.SetLogEntity
import com.myvitory.app.domain.SessionStatus
import java.time.LocalDate

/**
 * Os dias de treino como o painel mostra: um documento por data, com o que foi planejado e o que foi feito
 * (séries, cargas, comentário). Só o celular escreve; o treinador só lê.
 */
class HistoryExport(private val db: MyVitoryDatabase, private val training: TrainingRepository) {
    private val sessions = db.sessions()
    private val setLogs = db.setLogs()
    private val templates = db.templates()

    suspend fun days(from: LocalDate, to: LocalDate): Map<LocalDate, Map<String, Any?>> {
        val rows = sessions.getRange(from, to)
        if (rows.isEmpty()) return emptyMap()
        val names = db.exercises().getAll().associate { it.id to it.name }
        val blocked = db.availability().getRange(from, to).map { it.date }.toSet()
        val closed = db.gymClosed(from, to)
        // Em blocos: o SQLite limita a quantidade de parâmetros por consulta.
        val logs = rows.map { it.id }.chunked(500).flatMap { setLogs.getForSessions(it) }.groupBy { it.sessionId }
        return rows.associate { session -> session.date to document(session, logs[session.id].orEmpty(), names, session.date in blocked, closed[session.date]) }
    }

    private suspend fun document(session: SessionEntity, logs: List<SetLogEntity>, names: Map<String, String>, unavailable: Boolean, closed: String?): Map<String, Any?> {
        val planned = if (session.status == SessionStatus.REST) emptyList() else training.itemsFor(session)
        val plannedById = planned.associateBy { it.exerciseId }
        val exercises = if (logs.isNotEmpty()) {
            // Mesma ordem da tela do treino: posição e, depois de uma troca, o exercício que veio primeiro.
            logs.groupBy { it.position to it.exerciseId }.entries
                .sortedWith(compareBy({ it.key.first }, { it.value.minOf { log -> log.id } }))
                .map { (key, sets) ->
                    exercise(key.second, names, plannedById[key.second]?.note.orEmpty(), sets.sortedBy { it.setIndex }.map { set ->
                        mapOf("kg" to set.weightKg, "reps" to set.reps.toLong(), "done" to set.done)
                    })
                }
        } else {
            // Ainda não começou (ou foi marcado sem séries): manda o que estava planejado, sem cargas.
            planned.map { item ->
                exercise(item.exerciseId, names, item.note, List(item.sets) { mapOf("kg" to null, "reps" to item.reps.toLong(), "done" to false) })
            }
        }
        return mapOf(
            "date" to session.date.toString(),
            "name" to session.name,
            "status" to session.status.name,
            "templateId" to session.templateId?.let { templates.getTemplate(it) }?.let { if (it.weekOnly) templates.getBaseByCode(it.code)?.remoteId else it.remoteId },
            "special" to session.special,
            "unavailable" to unavailable,
            "closed" to closed,
            "movedTo" to session.movedTo?.toString(),
            "startedAt" to session.startedAt,
            "finishedAt" to session.finishedAt,
            "comment" to session.comment,
            "exercises" to exercises,
        )
    }

    private fun exercise(id: String, names: Map<String, String>, note: String, sets: List<Map<String, Any?>>): Map<String, Any?> =
        mapOf("exerciseId" to id, "name" to (names[id] ?: id), "note" to note, "sets" to sets)
}
