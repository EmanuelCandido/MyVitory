package com.myvitory.app.data.sync

import androidx.room.withTransaction
import com.myvitory.app.data.MAX_REST_SEC
import com.myvitory.app.data.MAX_TEXT
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.DatePlanEntity
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.PlanDayEntity
import com.myvitory.app.data.db.TemplateEntity
import com.myvitory.app.data.db.TemplateItemEntity
import com.myvitory.app.data.db.TombstoneEntity
import com.myvitory.app.data.exerciseKey
import com.myvitory.app.data.templateKey
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.domain.sync.PlanSnapshot
import com.myvitory.app.domain.sync.SnapshotDate
import com.myvitory.app.domain.sync.SnapshotDay
import com.myvitory.app.domain.sync.SnapshotExercise
import com.myvitory.app.domain.sync.SnapshotItem
import com.myvitory.app.domain.sync.SnapshotTemplate
import java.time.DayOfWeek
import java.time.LocalDate

/** O que um sync mudou no celular, para avisar a pessoa. */
data class ImportResult(
    val changedTemplates: List<String> = emptyList(),
    val deletedTemplates: List<String> = emptyList(),
    val weekChanged: Boolean = false,
    val exercisesChanged: Int = 0,
    /** Datas que mudaram (feriado ou treino escolhido para o dia). */
    val datesChanged: Int = 0,
) {
    val isEmpty get() = changedTemplates.isEmpty() && deletedTemplates.isEmpty() && !weekChanged && exercisesChanged == 0 && datesChanged == 0
}

/**
 * Ponte entre o Room e o [PlanSnapshot]: tira a foto do plano base do celular e aplica a versão decidida
 * pelo sync. Cópias "só nesta semana", sessões e histórico não entram aqui (o histórico vai em [HistoryExport]).
 */
class PlanSyncStore(private val db: MyVitoryDatabase, private val training: TrainingRepository) {
    private val templates = db.templates()
    private val plan = db.plan()
    private val exercises = db.exercises()
    private val sync = db.sync()
    private val datePlans = db.datePlans()

    suspend fun export(): PlanSnapshot = db.withTransaction {
        val base = templates.getBaseTemplates()
        val remoteIds = base.associate { it.id to it.remoteId }
        PlanSnapshot(
            templates = base.associate { t ->
                t.remoteId to SnapshotTemplate(t.code, t.name, t.updatedAt, templates.getItems(t.id).map { it.toSnapshot() })
            },
            week = plan.getPlan().associate { it.dayOfWeek to SnapshotDay(it.templateId?.let(remoteIds::get), it.updatedAt, it.closed) },
            exercises = exercises.getCustom().associate { e ->
                e.id to SnapshotExercise(e.name, e.equipment, e.primary.map { it.name }, e.secondary.map { it.name }, e.updatedAt)
            },
            deleted = sync.tombstones().associate { it.key to it.deletedAt },
            dates = datePlans.getAll().associate { d ->
                d.date.toString() to SnapshotDate(d.closed, d.label, d.override, d.templateRemoteId, d.updatedAt)
            },
        )
    }

    /**
     * Deixa o celular igual a [target]. [before] é a foto tirada no começo deste sync: o que a pessoa mudou
     * no celular enquanto o sync rodava não é sobrescrito (vai no próximo).
     */
    suspend fun import(target: PlanSnapshot, before: PlanSnapshot): ImportResult {
        val changed = mutableListOf<String>()
        val renamed = mutableListOf<Long>()
        val toDelete = mutableListOf<TemplateEntity>()
        val exercisesToDelete = mutableListOf<String>()
        var exercisesChanged = 0
        var weekChanged = false
        val reopenedDays = mutableSetOf<DayOfWeek>()
        val dateChanges = mutableListOf<Pair<DatePlanEntity?, DatePlanEntity>>()

        db.withTransaction {
            // 1. Exercícios próprios primeiro: um treino novo pode usar um deles.
            target.exercises.forEach { (id, e) ->
                val current = exercises.get(id)
                if (current != null && (!current.custom || current.updatedAt != before.exercises[id]?.updatedAt)) return@forEach
                val primary = e.primary.toMuscles()
                val entity = ExerciseEntity(id, e.name, e.equipment, primary, e.secondary.toMuscles() - primary.toSet(), custom = true, updatedAt = e.updatedAt)
                when {
                    current == null -> exercises.insert(entity)
                    current != entity -> exercises.update(entity)
                    else -> return@forEach
                }
                exercisesChanged++
            }
            exercises.getCustom().forEach { e ->
                val unchanged = e.updatedAt == before.exercises[e.id]?.updatedAt
                if (e.id !in target.exercises && exerciseKey(e.id) in target.deleted && unchanged) exercisesToDelete += e.id
            }

            // 2. Treinos do plano base.
            val known = exercises.getAll().map { it.id }.toSet()
            val base = templates.getBaseTemplates().associateBy { it.remoteId }
            target.templates.forEach { (remoteId, t) ->
                val current = base[remoteId]
                if (current != null && current.updatedAt != before.templates[remoteId]?.updatedAt) return@forEach
                // Exercício que este app ainda não conhece (catálogo do painel mais novo) fica de fora até atualizar.
                // Valores fora dos limites do app são ajustados aqui, e a comparação usa os ajustados: sem regravar a cada sync.
                val items = t.items.filter { it.exerciseId in known }.map { it.normalized() }
                if (current == null) {
                    val id = templates.insertTemplate(TemplateEntity(code = t.code, name = t.name, remoteId = remoteId, updatedAt = t.updatedAt))
                    insertItems(id, items)
                    changed += t.name
                    return@forEach
                }
                val sameItems = templates.getItems(current.id).map { it.toSnapshot() } == items
                if (current.code == t.code && current.name == t.name && current.updatedAt == t.updatedAt && sameItems) return@forEach
                templates.updateTemplate(current.copy(code = t.code, name = t.name, updatedAt = t.updatedAt))
                if (!sameItems) {
                    templates.deleteItems(current.id)
                    insertItems(current.id, items)
                }
                if (!sameItems || current.name != t.name) changed += t.name
                if (current.name != t.name) renamed += current.id
            }
            base.values.forEach { t ->
                val unchanged = t.updatedAt == before.templates[t.remoteId]?.updatedAt
                if (t.remoteId !in target.templates && templateKey(t.remoteId) in target.deleted && unchanged) toDelete += t
            }

            // 3. Semana do plano base.
            val byRemote = templates.getBaseTemplates().associateBy { it.remoteId }
            val deletedIds = toDelete.map { it.id }.toSet()
            val currentPlan = plan.getPlan().associateBy { it.dayOfWeek }
            target.week.forEach { (day, d) ->
                val current = currentPlan[day]
                if (current != null && current.updatedAt != before.week[day]?.updatedAt) return@forEach
                val localId = d.templateId?.let { byRemote[it]?.id }?.takeIf { it !in deletedIds }
                val wasClosed = current?.closed ?: false
                if (current != null && current.templateId == localId && current.updatedAt == d.updatedAt && wasClosed == d.closed) return@forEach
                if (current?.templateId != localId || wasClosed != d.closed) weekChanged = true
                if (wasClosed && !d.closed) reopenedDays += DayOfWeek.of(day)
                plan.upsert(listOf(PlanDayEntity(day, localId, d.updatedAt, d.closed)))
            }

            // 3b. Datas: feriados e treino escolhido para o dia.
            val localDates = datePlans.getAll().associateBy { it.date.toString() }
            target.dates.forEach { (key, d) ->
                val current = localDates[key]
                if (current != null && current.updatedAt != before.dates[key]?.updatedAt) return@forEach
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
                val entity = DatePlanEntity(date, d.closed, d.label.trim().take(MAX_LABEL), d.override, d.templateId, d.updatedAt)
                if (entity == current) return@forEach
                datePlans.upsert(listOf(entity))
                dateChanges += current to entity
            }
            // Datas antigas que o sync deixou de guardar.
            val gone = localDates.filterKeys { it !in target.dates && before.dates[it]?.updatedAt == localDates[it]?.updatedAt }
            if (gone.isNotEmpty()) datePlans.delete(gone.values.map { it.date })

            // 4. Apagamentos conhecidos: os decididos agora mais os que nasceram no celular durante este sync.
            val current = sync.tombstones().associate { it.key to it.deletedAt }
            val wanted = target.deleted + current.filter { (key, at) -> before.deleted[key] != at && target.deleted[key] == null }
            if (wanted != current) {
                sync.clearTombstones()
                sync.addTombstones(wanted.map { (key, at) -> TombstoneEntity(key, at) })
            }
        }

        // Fora da transação: cada um mexe nas sessões da semana com as regras de sempre.
        toDelete.forEach { training.deleteTemplate(it.id, tombstone = false) }
        exercisesToDelete.forEach { id ->
            if (exercises.usageCount(id) == 0) {
                exercises.deleteCustom(id)
                db.videos().unpin(id)
                exercisesChanged++
            }
        }
        renamed.forEach { training.renameUpcoming(it) }
        if (reopenedDays.isNotEmpty()) training.reopenWeekdays(reopenedDays)
        dateChanges.forEach { (before, after) -> training.applyDatePlan(after.date, before, after) }
        if (weekChanged) training.refreshUpcomingSessions()
        // Feriado ou dia fechado novo: o treino desse dia vai para o recálculo.
        if (weekChanged || dateChanges.isNotEmpty()) training.ensureWeek(WeekLogic.weekStartOf(training.today()))

        return ImportResult(changed, toDelete.map { it.name }, weekChanged, exercisesChanged, dateChanges.size)
    }

    private companion object {
        const val MAX_LABEL = 60
    }

    private suspend fun insertItems(templateId: Long, items: List<SnapshotItem>) {
        items.forEachIndexed { index, item ->
            templates.insertItem(
                TemplateItemEntity(
                    templateId = templateId, exerciseId = item.exerciseId, position = index,
                    sets = item.sets, reps = item.reps, restSec = item.restSec, note = item.note,
                )
            )
        }
    }

    private fun SnapshotItem.normalized() = copy(
        sets = sets.coerceIn(1, 10),
        reps = reps.coerceIn(1, 50),
        restSec = restSec.coerceIn(0, MAX_REST_SEC),
        note = note.trim().take(MAX_TEXT),
    )

    private fun TemplateItemEntity.toSnapshot() = SnapshotItem(exerciseId, sets, reps, restSec, note)

    private fun List<String>.toMuscles(): List<Muscle> = mapNotNull { name -> Muscle.entries.firstOrNull { it.name == name } }
}
