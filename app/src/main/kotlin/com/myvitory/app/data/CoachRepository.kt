package com.myvitory.app.data

import androidx.room.withTransaction
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.domain.MuscleLoads
import com.myvitory.app.domain.MuscleRecovery
import com.myvitory.app.domain.Recovery
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.TrainingEvent
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.domain.loadsOf
import com.myvitory.app.domain.planner.Planner
import com.myvitory.app.domain.planner.PlannerInput
import com.myvitory.app.domain.planner.Proposal
import com.myvitory.app.domain.planner.ReplanResult
import com.myvitory.app.domain.planner.Slot
import com.myvitory.app.domain.planner.Training
import com.myvitory.app.domain.planner.Workout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Entrada e resultado de um recálculo, guardados juntos para explicar e aplicar a proposta. */
data class ReplanData(val missed: List<SessionEntity>, val input: PlannerInput, val result: ReplanResult)

/** Fotografia das linhas mexidas por um recálculo aplicado, para o "Desfazer". */
data class ReplanUndo(val before: List<SessionEntity>, val insertedIds: List<Long>)

/** Treino de hoje e seus músculos, para a checagem da tela Recuperação. */
data class TodayLoads(val session: SessionEntity, val loads: MuscleLoads)

/**
 * Recálculo da semana e recuperação muscular (Fase 2).
 * Lê o mesmo banco do [TrainingRepository]; a lógica de decisão fica em domain/planner.
 */
class CoachRepository(
    private val db: MyVitoryDatabase,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Horas a mais de descanso por músculo (nível iniciante). */
    private val extraRecoveryHours: suspend () -> Int = { 0 },
) {
    private val sessions = db.sessions()
    private val templates = db.templates()
    private val plan = db.plan()
    private val exercises = db.exercises()
    private val setLogs = db.setLogs()

    private var catalogCache: Map<String, ExerciseEntity> = emptyMap()

    private suspend fun catalog(): Map<String, ExerciseEntity> {
        if (catalogCache.isEmpty()) catalogCache = exercises.getAll().associateBy { it.id }
        return catalogCache
    }

    private suspend fun loadsFor(exerciseIds: List<String>): MuscleLoads {
        val byId = catalog()
        return loadsOf(exerciseIds.mapNotNull { byId[it] }.map { it.primary to it.secondary })
    }

    suspend fun templateLoads(templateId: Long): MuscleLoads = loadsFor(templates.getItems(templateId).map { it.exerciseId })

    /** Músculos realmente treinados: as séries marcadas; sem séries (marcou "feito" direto), o treino inteiro. */
    suspend fun sessionLoads(session: SessionEntity): MuscleLoads {
        val done = setLogs.doneExerciseIds(session.id)
        if (done.isNotEmpty()) return loadsFor(done)
        return mergeLoads(session.templateId?.let { templateLoads(it) }.orEmpty(), session.mergedTemplateId?.let { templateLoads(it) }.orEmpty())
    }

    // ---- Recálculo ----

    fun observePendingMissed(): Flow<List<SessionEntity>> {
        val start = WeekLogic.weekStartOf(today())
        return sessions.observePendingMissed(start, start.plusDays(6))
    }

    /** Monta o problema da semana atual e roda o motor. Null quando não há treino perdido pendente. */
    suspend fun buildReplan(): ReplanData? {
        val date = today()
        val weekStart = WeekLogic.weekStartOf(date)
        val weekEnd = weekStart.plusDays(6)
        val week = sessions.getRange(weekStart, weekEnd)
        val missed = week.filter { it.status == SessionStatus.MISSED && !it.resolved && it.templateId != null }
        if (missed.isEmpty()) return null

        val planMap = plan.getPlan().associate { DayOfWeek.of(it.dayOfWeek) to it.templateId }
        val byDate = week.associateBy { it.date }
        val blocked = db.availability().getRange(date, weekEnd).map { it.date }.toSet()
        val slots = mutableListOf<Slot>()
        val upcoming = mutableListOf<SessionEntity>()
        var day = date
        while (!day.isAfter(weekEnd)) {
            val row = byDate[day]
            val free = day !in blocked && when (row?.status) {
                null, SessionStatus.REST -> true
                SessionStatus.PLANNED -> row.startedAt == null && setLogs.countForSession(row.id) == 0
                else -> false
            }
            if (free) {
                slots += Slot(day, preferredRest = planMap[day.dayOfWeek] == null)
                if (row?.status == SessionStatus.PLANNED && row.templateId != null) upcoming += row
            }
            day = day.plusDays(1)
        }

        val history = sessions.getTrainedSince(weekStart.minusDays(7)).map { Training(it.date, it.name, sessionLoads(it)) }
        val nextWeekStart = weekStart.plusWeeks(1)
        val nextWeek = WeekLogic.daysOf(nextWeekStart).mapNotNull { d ->
            val templateId = planMap[d.dayOfWeek] ?: return@mapNotNull null
            val template = templates.getTemplate(templateId) ?: return@mapNotNull null
            Training(d, template.name, templateLoads(templateId))
        }
        suspend fun workout(s: SessionEntity, missed: Boolean) =
            Workout(s.id, s.templateId!!, s.name, templateLoads(s.templateId), s.date, missed, exerciseCount = templates.getItems(s.templateId).size)
        val pending = missed.map { workout(it, missed = true) } + upcoming.map { workout(it, missed = false) }

        val extra = extraRecoveryHours()
        val input = PlannerInput(pending = pending, slots = slots, history = history, nextWeek = nextWeek, recoveryHours = { it.recoveryHours + extra })
        val result = withContext(Dispatchers.Default) { Planner.replan(input) }
        return ReplanData(missed, input, result)
    }

    /** Grava a proposta: cada dia livre recebe o treino escolhido ou vira descanso. */
    suspend fun apply(data: ReplanData, proposal: Proposal): ReplanUndo = db.withTransaction {
        val planMap = plan.getPlan().associate { DayOfWeek.of(it.dayOfWeek) to it.templateId }
        val before = mutableListOf<SessionEntity>()
        val inserted = mutableListOf<Long>()
        data.input.slots.forEach { slot ->
            val row = sessions.getByDate(slot.date)
            row?.let { before += it }
            val workout = proposal.assignment[slot.date]
            when {
                workout != null && row != null ->
                    sessions.update(
                        row.copy(
                            templateId = workout.templateId, mergedTemplateId = workout.mergedWith?.templateId,
                            name = workout.name, status = SessionStatus.PLANNED,
                        )
                    )
                workout != null ->
                    inserted += sessions.insert(
                        SessionEntity(
                            date = slot.date, templateId = workout.templateId, mergedTemplateId = workout.mergedWith?.templateId,
                            name = workout.name, status = SessionStatus.PLANNED,
                        )
                    )
                row != null ->
                    sessions.update(row.copy(templateId = null, mergedTemplateId = null, name = REST_NAME, status = SessionStatus.REST))
                planMap[slot.date.dayOfWeek] != null ->
                    inserted += sessions.insert(SessionEntity(date = slot.date, templateId = null, name = REST_NAME, status = SessionStatus.REST))
            }
        }
        data.missed.forEach { missedSession ->
            val current = sessions.get(missedSession.id) ?: return@forEach
            before += current
            val workout = data.input.pending.first { it.sessionId == missedSession.id }
            sessions.update(current.copy(resolved = true, movedTo = proposal.dateOf(workout)))
        }
        ReplanUndo(before, inserted)
    }

    suspend fun undo(undo: ReplanUndo) = db.withTransaction {
        undo.insertedIds.forEach { sessions.delete(it) }
        undo.before.forEach { sessions.update(it) }
    }

    /** "Pular": o treino perdido fica perdido e o app para de sugerir o recálculo. */
    suspend fun dismiss(missed: List<SessionEntity>): ReplanUndo = db.withTransaction {
        val before = missed.mapNotNull { sessions.get(it.id) }
        before.forEach { sessions.update(it.copy(resolved = true)) }
        ReplanUndo(before, emptyList())
    }

    // ---- Recuperação ----

    /** Recuperação por músculo a partir dos treinos das últimas duas semanas. Reavaliada a cada emissão do banco. */
    fun observeRecoveryEvents(): Flow<List<TrainingEvent>> =
        sessions.observeTrainedSince(today().minusDays(14)).map { trained ->
            trained.map { TrainingEvent(eventTime(it), sessionLoads(it)) }
        }

    fun recoveryNow(events: List<TrainingEvent>, extraHours: Int = 0): List<MuscleRecovery> =
        Recovery.status(events, clock()) { it.recoveryHours + extraHours }

    fun observeTodayLoads(): Flow<TodayLoads?> {
        val date = today()
        return sessions.observeRange(date, date).map { rows ->
            val session = rows.firstOrNull()?.takeIf { it.status == SessionStatus.PLANNED && it.templateId != null }
            session?.let { TodayLoads(it, mergeLoads(templateLoads(it.templateId!!), it.mergedTemplateId?.let { id -> templateLoads(id) }.orEmpty())) }
        }
    }

    /** Horário real do treino; sem registro de horário, considera o fim da tarde daquele dia. */
    private fun eventTime(session: SessionEntity): Long =
        session.finishedAt ?: session.startedAt ?: session.date.atTime(DEFAULT_TRAINING_TIME).atZone(zone).toInstant().toEpochMilli()

    fun now(): Long = clock()

    private fun mergeLoads(a: MuscleLoads, b: MuscleLoads): MuscleLoads =
        (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0.0, b[it] ?: 0.0) }

    companion object {
        const val REST_NAME = "Descanso"
        private val DEFAULT_TRAINING_TIME: LocalTime = LocalTime.of(19, 0)
    }
}
