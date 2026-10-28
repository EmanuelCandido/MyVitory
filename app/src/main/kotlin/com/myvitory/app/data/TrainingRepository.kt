package com.myvitory.app.data

import androidx.room.withTransaction
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.PlanDayEntity
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.SetLogEntity
import com.myvitory.app.data.db.TemplateEntity
import com.myvitory.app.data.db.TemplateItemEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DayView(
    val date: LocalDate,
    val session: SessionEntity?,
    val exerciseCount: Int,
    val state: DayState,
)

data class WorkoutExercise(
    val exerciseId: String,
    val name: String,
    val position: Int,
    val sets: List<SetLogEntity>,
    /** Ex.: "140 kg × 10, 10, 9". Nulo quando é a primeira vez. */
    val lastTime: String?,
)

data class WorkoutView(val session: SessionEntity, val exercises: List<WorkoutExercise>) {
    val totalSets get() = exercises.sumOf { it.sets.size }
    val doneSets get() = exercises.sumOf { ex -> ex.sets.count { it.done } }
}

data class PlanRow(val day: DayOfWeek, val template: TemplateEntity?, val exerciseCount: Int)

data class TemplateSummary(val template: TemplateEntity, val exerciseCount: Int)

class TrainingRepository(
    private val db: MyVitoryDatabase,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val exercises = db.exercises()
    private val templates = db.templates()
    private val plan = db.plan()
    private val sessions = db.sessions()
    private val setLogs = db.setLogs()

    private val seedLock = Mutex()

    fun today(): LocalDate = today.invoke()

    suspend fun seedIfEmpty() = seedLock.withLock {
        // Exercícios novos do catálogo chegam também a quem já usa o app (IGNORE não mexe nos existentes).
        exercises.insertAll(SeedData.exercises)
        if (templates.codes().isNotEmpty()) return@withLock
        db.withTransaction {
            val planDays = DayOfWeek.entries.associateWith<DayOfWeek, Long?> { null }.toMutableMap()
            SeedData.templates.forEach { t ->
                val id = templates.insertTemplate(TemplateEntity(code = t.code, name = t.name))
                t.items.forEachIndexed { index, item ->
                    templates.insertItem(
                        TemplateItemEntity(
                            templateId = id, exerciseId = item.exerciseId, position = index,
                            sets = item.sets, reps = item.reps, restSec = item.restSec,
                        )
                    )
                }
                planDays[t.day] = id
            }
            plan.upsert(planDays.map { (day, id) -> PlanDayEntity(day.value, id) })
        }
    }

    /** Cria as sessões planejadas da semana que ainda não existem. */
    suspend fun ensureWeek(weekStart: LocalDate) {
        val planMap = plan.getPlan().associate { DayOfWeek.of(it.dayOfWeek) to it.templateId }
        val existing = sessions.getRange(weekStart, weekStart.plusDays(6)).map { it.date }.toSet()
        val toCreate = WeekLogic.sessionsToCreate(weekStart, planMap, existing)
        if (toCreate.isEmpty()) return
        sessions.insertAll(
            toCreate.mapNotNull { (date, templateId) ->
                val template = templates.getTemplate(templateId) ?: return@mapNotNull null
                SessionEntity(date = date, templateId = templateId, name = template.name, status = SessionStatus.PLANNED)
            }
        )
    }

    fun observeWeek(weekStart: LocalDate): Flow<List<DayView>> =
        combine(
            sessions.observeRange(weekStart, weekStart.plusDays(6)),
            templates.observeItemCounts(),
        ) { weekSessions, counts ->
            val byDate = weekSessions.associateBy { it.date }
            val countByTemplate = counts.associate { it.templateId to it.count }
            WeekLogic.daysOf(weekStart).map { date ->
                // Linha REST é só um descanso decidido: para a tela, o dia não tem treino.
                val session = byDate[date]?.takeIf { it.status != SessionStatus.REST }
                DayView(
                    date = date,
                    session = session,
                    exerciseCount = session?.templateId?.let { countByTemplate[it] } ?: 0,
                    state = WeekLogic.dayState(date, today(), session?.status, moved = session?.movedTo != null),
                )
            }
        }

    fun observeTemplateItems(templateId: Long): Flow<List<TemplateItemRow>> = templates.observeItems(templateId)

    fun observeExercises(): Flow<List<ExerciseEntity>> = exercises.observeAll()

    // ---- Treino em andamento ----

    /** Na primeira vez cria as séries a partir do treino, já com a carga da última vez. */
    suspend fun startWorkout(sessionId: Long) {
        val session = sessions.get(sessionId) ?: return
        if (setLogs.countForSession(sessionId) == 0) {
            val items = session.templateId?.let { templates.getItems(it) }.orEmpty()
            val logs = items.flatMap { item ->
                val last = setLogs.lastDone(item.exerciseId, session.date)
                (0 until item.sets).map { index ->
                    val previous = last.getOrNull(index) ?: last.lastOrNull()
                    SetLogEntity(
                        sessionId = sessionId,
                        exerciseId = item.exerciseId,
                        position = item.position,
                        setIndex = index,
                        weightKg = previous?.weightKg ?: 0.0,
                        reps = item.reps,
                        restSec = item.restSec,
                    )
                }
            }
            setLogs.insertAll(logs)
        }
        // Abrir um treino já fechado (para ver ou corrigir) não reinicia o cronômetro.
        if (session.startedAt == null && session.status == SessionStatus.PLANNED) {
            sessions.update(session.copy(startedAt = clock()))
        }
    }

    fun observeWorkout(sessionId: Long): Flow<WorkoutView?> {
        val lastTimeCache = mutableMapOf<String, String?>()
        return combine(
            sessions.observe(sessionId),
            setLogs.observeForSession(sessionId),
            exercises.observeAll(),
        ) { session, logs, catalog -> Triple(session, logs, catalog) }
            .map { (session, logs, catalog) ->
                session ?: return@map null
                val names = catalog.associate { it.id to it.name }
                // Depois de uma troca, a mesma posição pode ter dois exercícios: o original (séries feitas) e o novo.
                val grouped = logs.groupBy { it.position to it.exerciseId }.entries
                    .sortedWith(compareBy({ it.key.first }, { it.value.minOf { log -> log.id } }))
                WorkoutView(
                    session = session,
                    exercises = grouped.map { (key, sets) ->
                        val exerciseId = key.second
                        val lastTime = lastTimeCache.getOrPut(exerciseId) {
                            formatLastTime(setLogs.lastDone(exerciseId, session.date))
                        }
                        WorkoutExercise(exerciseId, names[exerciseId] ?: exerciseId, key.first, sets, lastTime)
                    },
                )
            }
    }

    suspend fun updateSet(log: SetLogEntity) = setLogs.update(log)

    suspend fun addSet(sessionId: Long, exerciseId: String) {
        val sets = setLogs.getForSession(sessionId).filter { it.exerciseId == exerciseId }
        val last = sets.maxByOrNull { it.setIndex } ?: return
        setLogs.insertAll(listOf(last.copy(id = 0, setIndex = last.setIndex + 1, done = false)))
    }

    suspend fun removeSet(log: SetLogEntity) = setLogs.delete(log.id)

    /** O que é preciso para desfazer uma troca de exercício. */
    data class SwapUndo(
        val removed: List<SetLogEntity>,
        val insertedIds: List<Long>,
        val templateId: Long?,
        val from: String,
        val to: String,
    )

    /**
     * Troca o exercício no treino em andamento: as séries ainda não feitas passam para [to],
     * na mesma posição; as já feitas ficam registradas com o exercício original.
     * [alsoInPlan] troca também no treino do plano, valendo para as próximas semanas.
     */
    suspend fun swapExercise(sessionId: Long, from: String, to: String, alsoInPlan: Boolean): SwapUndo? {
        if (from == to) return null
        val session = sessions.get(sessionId) ?: return null
        val last = setLogs.lastDone(to, session.date)
        return db.withTransaction {
            val open = setLogs.getForSession(sessionId).filter { it.exerciseId == from && !it.done }.sortedBy { it.setIndex }
            val replacements = open.mapIndexed { i, log ->
                val previous = last.getOrNull(i) ?: last.lastOrNull()
                log.copy(id = 0, exerciseId = to, setIndex = i, weightKg = previous?.weightKg ?: 0.0, done = false)
            }
            setLogs.deleteAll(open.map { it.id })
            val inserted = setLogs.insertAll(replacements)
            val templateId = session.templateId.takeIf { alsoInPlan }
            templateId?.let { templates.replaceExercise(it, from, to) }
            SwapUndo(open, inserted, templateId, from, to)
        }
    }

    suspend fun undoSwap(undo: SwapUndo) = db.withTransaction {
        setLogs.deleteAll(undo.insertedIds)
        setLogs.insertAll(undo.removed)
        undo.templateId?.let { templates.replaceExercise(it, undo.to, undo.from) }
    }

    fun observeExerciseIdsInPlans(): Flow<List<String>> = templates.observeExerciseIdsInPlans()

    /** Fecha o treino pelo que foi marcado. Nenhuma série marcada: não muda nada e devolve null. */
    suspend fun finishWorkout(sessionId: Long): SessionStatus? {
        val session = sessions.get(sessionId) ?: return null
        val logs = setLogs.getForSession(sessionId)
        val status = WeekLogic.statusOnFinish(logs.size, logs.count { it.done }) ?: return null
        sessions.update(session.copy(status = status, finishedAt = clock()))
        return status
    }

    // ---- Marcar concluído / não ----

    /** O que é preciso para desfazer uma marcação. */
    data class MarkUndo(val previous: SessionEntity, val logsMarkedDone: List<SetLogEntity>)

    /** Muda o status. "Concluí tudo" também marca as séries abertas, para a carga entrar no histórico. */
    suspend fun markSession(sessionId: Long, status: SessionStatus): MarkUndo? {
        val session = sessions.get(sessionId) ?: return null
        return db.withTransaction {
            val opened = if (status == SessionStatus.DONE) setLogs.getForSession(sessionId).filter { !it.done } else emptyList()
            opened.forEach { setLogs.update(it.copy(done = true)) }
            val newlyMissed = status == SessionStatus.MISSED
            sessions.update(
                session.copy(
                    status = status,
                    // Marcar um dia passado não pode carimbar "agora": a recuperação usaria o horário errado.
                    finishedAt = when {
                        status == SessionStatus.PLANNED -> null
                        session.finishedAt != null -> session.finishedAt
                        session.date == today() -> clock()
                        else -> null
                    },
                    // Perdido de novo: o recálculo volta a valer para ele.
                    resolved = if (newlyMissed) false else session.resolved,
                    movedTo = if (newlyMissed) null else session.movedTo,
                )
            )
            MarkUndo(session, opened)
        }
    }

    suspend fun undoMark(undo: MarkUndo) = db.withTransaction {
        undo.logsMarkedDone.forEach { setLogs.update(it) }
        sessions.update(undo.previous)
    }

    // ---- Plano base e treinos ----

    fun observePlanBase(): Flow<List<PlanRow>> =
        combine(plan.observePlan(), templates.observeTemplates(), templates.observeItemCounts()) { days, all, counts ->
            val byId = all.associateBy { it.id }
            val countById = counts.associate { it.templateId to it.count }
            DayOfWeek.entries.map { day ->
                val templateId = days.firstOrNull { it.dayOfWeek == day.value }?.templateId
                PlanRow(day, templateId?.let { byId[it] }, templateId?.let { countById[it] } ?: 0)
            }
        }

    fun observeTemplates(): Flow<List<TemplateSummary>> =
        combine(templates.observeTemplates(), templates.observeItemCounts()) { all, counts ->
            val countById = counts.associate { it.templateId to it.count }
            all.map { TemplateSummary(it, countById[it.id] ?: 0) }
        }

    fun observeTemplate(templateId: Long): Flow<TemplateEntity?> = templates.observeTemplate(templateId)

    suspend fun setPlanDay(day: DayOfWeek, templateId: Long?) {
        plan.upsert(listOf(PlanDayEntity(day.value, templateId)))
        refreshUpcomingSessions()
    }

    suspend fun createTemplate(): Long {
        val used = templates.codes().toSet()
        val code = ('A'..'Z').map { it.toString() }.firstOrNull { it !in used } ?: "${used.size + 1}"
        return templates.insertTemplate(TemplateEntity(code = code, name = "Treino $code"))
    }

    suspend fun renameTemplate(templateId: Long, name: String) {
        val template = templates.getTemplate(templateId) ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean == template.name) return
        templates.updateTemplate(template.copy(name = clean))
        refreshUpcomingSessions()
    }

    suspend fun addExerciseToTemplate(templateId: Long, exerciseId: String) {
        val position = templates.maxPosition(templateId) + 1
        templates.insertItem(
            TemplateItemEntity(
                templateId = templateId, exerciseId = exerciseId, position = position,
                sets = 3, reps = 10, restSec = 90,
            )
        )
    }

    suspend fun updateTemplateItem(itemId: Long, sets: Int, reps: Int) =
        templates.updateItemVolume(itemId, sets.coerceIn(1, 10), reps.coerceIn(1, 50))

    suspend fun removeTemplateItem(itemId: Long) = templates.deleteItem(itemId)

    /** Refaz as sessões de hoje em diante que ninguém começou, para refletir o plano novo. */
    private suspend fun refreshUpcomingSessions() {
        val date = today()
        sessions.deleteUntouchedFrom(date)
        ensureWeek(WeekLogic.weekStartOf(date))
    }

    private fun formatLastTime(last: List<SetLogEntity>): String? {
        if (last.isEmpty()) return null
        val weight = last.maxOf { it.weightKg }
        val reps = last.joinToString(", ") { it.reps.toString() }
        return if (weight > 0) "${formatKg(weight)} kg × $reps" else "$reps reps"
    }
}

fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg).replace('.', ',')
