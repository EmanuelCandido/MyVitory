package com.myvitory.app.data

import androidx.room.withTransaction
import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.data.db.ExerciseHistoryPoint
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.PlanDayEntity
import com.myvitory.app.data.db.SessionEntity
import com.myvitory.app.data.db.SetLogEntity
import com.myvitory.app.data.db.TemplateEntity
import com.myvitory.app.data.db.TemplateItemEntity
import com.myvitory.app.data.db.TemplateItemRow
import com.myvitory.app.data.db.TombstoneEntity
import com.myvitory.app.data.db.UnavailableDayEntity
import com.myvitory.app.data.db.newRemoteId
import com.myvitory.app.domain.DayState
import com.myvitory.app.domain.DoneSet
import com.myvitory.app.domain.LoadSuggestion
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.Progression
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DayView(
    val date: LocalDate,
    val session: SessionEntity?,
    val exerciseCount: Int,
    val state: DayState,
    /** A pessoa avisou que não pode treinar neste dia. */
    val unavailable: Boolean = false,
    /** Mudado só para esta semana (inclusive um descanso). */
    val special: Boolean = false,
)

data class WorkoutExercise(
    val exerciseId: String,
    val name: String,
    val position: Int,
    val sets: List<SetLogEntity>,
    /** Ex.: "140 kg × 10, 10, 9". Nulo quando é a primeira vez. */
    val lastTime: String?,
    /** Carga subiu porque todas as repetições foram batidas na última vez. */
    val suggestion: LoadSuggestion? = null,
    /** Observação do treino para este exercício ("descer em 3 s"). Vazia quando não há. */
    val note: String = "",
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
    private val availability = db.availability()
    private val sync = db.sync()

    private val seedLock = Mutex()

    fun today(): LocalDate = today.invoke()

    fun observeToday(): Flow<LocalDate> = observeDate(today)

    /** O app e a tela Hoje pedem a semente na abertura; basta uma vez por processo. */
    @Volatile private var seeded = false

    suspend fun seedIfEmpty() = seedLock.withLock {
        if (seeded) return@withLock
        // Exercícios novos do catálogo chegam também a quem já usa o app (IGNORE não mexe nos existentes).
        exercises.insertAll(SeedData.exercises)
        if (templates.codes().isNotEmpty()) {
            seeded = true
            return@withLock
        }
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
            // Primeira abertura: os dias que já passaram nesta semana viram descanso, não falta.
            val date = today()
            sessions.insertAll(
                WeekLogic.daysOf(WeekLogic.weekStartOf(date)).filter { it.isBefore(date) }
                    .map { SessionEntity(date = it, templateId = null, name = CoachRepository.REST_NAME, status = SessionStatus.REST) }
            )
        }
        // Só depois de gravar: se algo falhar, a próxima abertura tenta de novo.
        seeded = true
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
            templates.observeTemplateExercises(),
            availability.observeRange(weekStart, weekStart.plusDays(6)),
        ) { weekSessions, items, blocked ->
            val byDate = weekSessions.associateBy { it.date }
            val blockedDates = blocked.map { it.date }.toSet()
            val exercisesByTemplate = items.groupBy({ it.templateId }, { it.exerciseId }).mapValues { it.value.toSet() }
            WeekLogic.daysOf(weekStart).map { date ->
                // Linha REST é só um descanso decidido: para a tela, o dia não tem treino.
                val session = byDate[date]?.takeIf { it.status != SessionStatus.REST }
                DayView(
                    date = date,
                    session = session,
                    // Treino fundido: exercício que aparece nos dois treinos conta uma vez só.
                    exerciseCount = (
                        session?.templateId?.let { exercisesByTemplate[it] }.orEmpty() +
                            session?.mergedTemplateId?.let { exercisesByTemplate[it] }.orEmpty()
                        ).size,
                    state = WeekLogic.dayState(date, today(), session?.status, moved = session?.movedTo != null, unavailable = date in blockedDates),
                    unavailable = date in blockedDates,
                    special = byDate[date]?.special == true,
                )
            }
        }

    fun observeTemplateItems(templateId: Long): Flow<List<TemplateItemRow>> = templates.observeItems(templateId)

    fun observeExercises(): Flow<List<ExerciseEntity>> = exercises.observeAll()

    // ---- Exercícios do usuário ----

    /** Cria um exercício próprio e devolve o id. */
    suspend fun createExercise(name: String, equipment: String, primary: List<Muscle>, secondary: List<Muscle>): String {
        // Id aleatório: o painel do treinador também cria exercícios, e os dois lados não podem gerar o mesmo.
        val id = "custom_${newRemoteId().take(16)}"
        exercises.insert(ExerciseEntity(id, name.trim(), equipment, primary, secondary - primary.toSet(), custom = true, updatedAt = clock()))
        return id
    }

    suspend fun updateExercise(id: String, name: String, equipment: String, primary: List<Muscle>, secondary: List<Muscle>) {
        val current = exercises.get(id)?.takeIf { it.custom } ?: return
        exercises.update(
            current.copy(name = name.trim(), equipment = equipment, primary = primary, secondary = secondary - primary.toSet(), updatedAt = clock())
        )
    }

    /** Apaga só exercício próprio que não está em nenhum treino nem no histórico. Devolve false quando não apagou. */
    suspend fun deleteExercise(id: String): Boolean = db.withTransaction {
        if (exercises.get(id)?.custom != true || exercises.usageCount(id) > 0) return@withTransaction false
        exercises.deleteCustom(id)
        db.videos().unpin(id)
        sync.addTombstones(listOf(TombstoneEntity(exerciseKey(id), clock())))
        true
    }

    // ---- Treino em andamento ----

    /**
     * Exercícios de uma sessão. Num treino fundido entram os dois treinos, sem repetir exercício,
     * com uma série a menos em cada um (mínimo de 2): o volume total cabe num dia.
     */
    internal suspend fun itemsFor(session: SessionEntity): List<TemplateItemEntity> {
        val main = session.templateId?.let { templates.getItems(it) }.orEmpty()
        val mergedId = session.mergedTemplateId ?: return main
        val offset = (main.maxOfOrNull { it.position } ?: -1) + 1
        val extra = templates.getItems(mergedId).filter { item -> main.none { it.exerciseId == item.exerciseId } }
        return (main + extra.map { it.copy(position = it.position + offset) }).map { it.copy(sets = mergedSets(it.sets)) }
    }

    /** Prévia da tela Hoje, já com a fusão. */
    fun observeSessionItems(templateId: Long, mergedTemplateId: Long?): Flow<List<TemplateItemRow>> {
        if (mergedTemplateId == null) return templates.observeItems(templateId)
        return combine(templates.observeItems(templateId), templates.observeItems(mergedTemplateId)) { main, extra ->
            (main + extra.filter { e -> main.none { it.exerciseId == e.exerciseId } }).map { it.copy(sets = mergedSets(it.sets)) }
        }
    }

    /**
     * Na primeira vez cria as séries a partir do treino, com a carga da última vez,
     * ou um degrau acima quando todas as repetições foram batidas (progressão automática).
     */
    suspend fun startWorkout(sessionId: Long) = db.withTransaction {
        val session = sessions.get(sessionId) ?: return@withTransaction
        // Na transação: abrir o treino duas vezes seguidas não cria as séries em dobro.
        if (setLogs.countForSession(sessionId) == 0) {
            val items = itemsFor(session)
            val equipment = exercises.getAll().associate { it.id to it.equipment }
            val logs = items.flatMap { item ->
                val last = setLogs.lastDone(item.exerciseId, session.date)
                val suggestion = suggestionFor(last, item.reps, item.sets, equipment[item.exerciseId].orEmpty())
                (0 until item.sets).map { index ->
                    val previous = last.getOrNull(index) ?: last.lastOrNull()
                    SetLogEntity(
                        sessionId = sessionId,
                        exerciseId = item.exerciseId,
                        position = item.position,
                        setIndex = index,
                        weightKg = suggestion?.weightKg ?: previous?.weightKg ?: 0.0,
                        reps = item.reps,
                        restSec = item.restSec,
                    )
                }
            }
            setLogs.insertAll(logs)
        }
        // Abrir um treino já fechado (para ver ou corrigir) não reinicia o cronômetro.
        // Registrar depois um dia que passou não carimba "agora": a recuperação usaria o horário errado.
        if (session.startedAt == null && session.status == SessionStatus.PLANNED && session.date == today()) {
            sessions.update(session.copy(startedAt = clock()))
        }
    }

    fun observeWorkout(sessionId: Long): Flow<WorkoutView?> {
        // Histórico não muda durante o treino: calcula uma vez por exercício.
        val historyCache = mutableMapOf<String, Pair<String?, LoadSuggestion?>>()
        var targetCache: Map<String, TemplateItemEntity>? = null
        suspend fun targets(session: SessionEntity): Map<String, TemplateItemEntity> =
            targetCache ?: itemsFor(session).associateBy { it.exerciseId }.also { targetCache = it }
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
                        val (lastTime, suggestion) = historyCache.getOrPut(exerciseId) {
                            val last = setLogs.lastDone(exerciseId, session.date)
                            val target = targets(session)[exerciseId]
                            val reps = target?.reps ?: sets.first().reps
                            val setCount = target?.sets ?: sets.size
                            val equipment = catalog.firstOrNull { it.id == exerciseId }?.equipment.orEmpty()
                            formatLastTime(last) to suggestionFor(last, reps, setCount, equipment)
                        }
                        val note = targets(session)[exerciseId]?.note.orEmpty()
                        WorkoutExercise(exerciseId, names[exerciseId] ?: exerciseId, key.first, sets, lastTime, suggestion, note)
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
        /** Itens de treino que passaram a usar [to] ("No plano todo"). */
        val planItemIds: List<Long>,
        /** O plano base mudou (e não só a cópia "só nesta semana" deste dia). */
        val changedBasePlan: Boolean,
        val from: String,
        val to: String,
    )

    /**
     * Troca o exercício no treino em andamento: as séries ainda não feitas passam para [to],
     * na mesma posição; as já feitas ficam registradas com o exercício original.
     * [alsoInPlan] troca também nos treinos do plano por trás deste dia, valendo para as próximas semanas.
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
            val changed = if (!alsoInPlan) emptyList() else planTemplatesOf(session).flatMap { template ->
                templates.getItems(template.id).filter { it.exerciseId == from }.map { template to it.id }
            }
            val itemIds = changed.map { it.second }
            if (itemIds.isNotEmpty()) {
                templates.setExercise(itemIds, to)
                templates.touch(changed.map { it.first.id }.distinct(), clock())
            }
            SwapUndo(open, inserted, itemIds, changedBasePlan = changed.any { !it.first.weekOnly }, from, to)
        }
    }

    /**
     * Treinos que "No plano todo" muda: os do dia (o principal e o fundido) e, quando um deles é cópia
     * "só nesta semana", também o treino do plano base de onde a cópia saiu.
     */
    private suspend fun planTemplatesOf(session: SessionEntity): List<TemplateEntity> =
        listOfNotNull(session.templateId, session.mergedTemplateId).flatMap { id ->
            val template = templates.getTemplate(id) ?: return@flatMap emptyList()
            if (template.weekOnly) listOfNotNull(template, templates.getBaseByCode(template.code)) else listOf(template)
        }.distinctBy { it.id }

    suspend fun undoSwap(undo: SwapUndo) = db.withTransaction {
        setLogs.deleteAll(undo.insertedIds)
        setLogs.insertAll(undo.removed)
        if (undo.planItemIds.isNotEmpty()) {
            templates.setExercise(undo.planItemIds, undo.from)
            undo.planItemIds.forEach { touchItem(it) }
        }
    }

    fun observeExerciseIdsInPlans(): Flow<List<String>> = templates.observeExerciseIdsInPlans()

    data class AvailabilityUndo(val date: LocalDate, val wasUnavailable: Boolean, val previous: SessionEntity?)

    /**
     * "Não vou poder treinar neste dia". Um treino planejado ali vira perdido (o recálculo encaixa em outro dia)
     * e o dia deixa de ser usado pelo recálculo. Liberar o dia de novo devolve o treino se ninguém decidiu nada.
     */
    suspend fun setUnavailable(date: LocalDate, unavailable: Boolean): AvailabilityUndo = db.withTransaction {
        val wasUnavailable = availability.getRange(date, date).isNotEmpty()
        val session = sessions.getByDate(date)
        if (unavailable) {
            availability.add(UnavailableDayEntity(date))
            if (session != null && session.status == SessionStatus.PLANNED && session.startedAt == null && session.templateId != null) {
                sessions.update(session.copy(status = SessionStatus.MISSED, resolved = false, movedTo = null))
            }
        } else {
            availability.remove(date)
            if (session != null && session.status == SessionStatus.MISSED && !session.resolved && !date.isBefore(today())) {
                sessions.update(session.copy(status = SessionStatus.PLANNED))
            }
        }
        AvailabilityUndo(date, wasUnavailable, session)
    }

    suspend fun undoAvailability(undo: AvailabilityUndo) = db.withTransaction {
        if (undo.wasUnavailable) availability.add(UnavailableDayEntity(undo.date)) else availability.remove(undo.date)
        undo.previous?.let { sessions.update(it) }
    }

    /** Evolução do exercício em ordem cronológica (mais antigo primeiro), para o gráfico. */
    fun observeHistory(exerciseId: String): Flow<List<ExerciseHistoryPoint>> =
        setLogs.observeHistory(exerciseId).map { it.reversed() }

    /** Fecha o treino pelo que foi marcado. Nenhuma série marcada: não muda nada e devolve null. */
    suspend fun finishWorkout(sessionId: Long): SessionStatus? {
        val session = sessions.get(sessionId) ?: return null
        val logs = setLogs.getForSession(sessionId)
        val status = WeekLogic.statusOnFinish(logs.size, logs.count { it.done }) ?: return null
        // Mesma regra do markSession: dia que passou fica sem horário (a recuperação usa o fim da tarde daquele dia).
        sessions.update(session.copy(status = status, finishedAt = if (session.date == today()) clock() else session.finishedAt))
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

    /** Comentário da pessoa sobre o treino. Vazio apaga. */
    suspend fun setSessionComment(sessionId: Long, comment: String) =
        sessions.setComment(sessionId, comment.trim().take(MAX_TEXT).ifEmpty { null })

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
        plan.upsert(listOf(PlanDayEntity(day.value, templateId, updatedAt = clock())))
        refreshUpcomingSessions()
    }

    // ---- Só nesta semana ----

    /** Troca o treino (ou põe descanso, com null) de um dia só nesta semana. Devolve false se o dia já começou. */
    suspend fun setDayThisWeek(date: LocalDate, templateId: Long?): Boolean = db.withTransaction {
        if (date.isBefore(today())) return@withTransaction false
        val current = sessions.getByDate(date)
        if (current != null && isTouched(current)) return@withTransaction false
        val template = templateId?.let { templates.getTemplate(it) ?: return@withTransaction false }
        current?.let { sessions.delete(it.id) }
        sessions.insert(
            SessionEntity(
                date = date,
                templateId = template?.id,
                name = template?.name ?: CoachRepository.REST_NAME,
                status = if (template == null) SessionStatus.REST else SessionStatus.PLANNED,
                special = true,
            )
        )
        current?.templateId?.let { templates.deleteUnusedWeekOnly(it) }
        sessions.reopenMovedTo(date, keepTemplateId = template?.id)
        true
    }

    /**
     * Prepara os exercícios de um dia para editar só nesta semana: copia o treino (já com a fusão, se houver)
     * e liga o dia à cópia. Devolve o id da cópia para abrir no editor, ou null se o dia já começou.
     */
    suspend fun customizeDayThisWeek(sessionId: Long): Long? = db.withTransaction {
        val session = sessions.get(sessionId) ?: return@withTransaction null
        if (session.date.isBefore(today()) || isTouched(session)) return@withTransaction null
        val templateId = session.templateId ?: return@withTransaction null
        val template = templates.getTemplate(templateId) ?: return@withTransaction null
        if (template.weekOnly && session.mergedTemplateId == null) return@withTransaction templateId
        val items = itemsFor(session)
        val copyId = templates.insertTemplate(TemplateEntity(code = template.code, name = session.name, weekOnly = true))
        items.forEachIndexed { index, item -> templates.insertItem(item.copy(id = 0, templateId = copyId, position = index)) }
        sessions.update(session.copy(templateId = copyId, mergedTemplateId = null, special = true))
        if (template.weekOnly) templates.deleteUnusedWeekOnly(templateId)
        copyId
    }

    /** Desfaz a mudança deste dia: volta o que o plano base manda. */
    suspend fun resetDayToPlan(date: LocalDate): Boolean {
        val reset = db.withTransaction {
            val current = sessions.getByDate(date) ?: return@withTransaction false
            if (date.isBefore(today()) || isTouched(current)) return@withTransaction false
            sessions.delete(current.id)
            current.templateId?.let { templates.deleteUnusedWeekOnly(it) }
            true
        }
        if (reset) {
            ensureWeek(WeekLogic.weekStartOf(date))
            sessions.reopenMovedTo(date, keepTemplateId = sessions.getByDate(date)?.templateId)
        }
        return reset
    }

    private suspend fun isTouched(session: SessionEntity) =
        session.startedAt != null || setLogs.countForSession(session.id) > 0 ||
            (session.status != SessionStatus.PLANNED && session.status != SessionStatus.REST)

    /** Dia que usa esta cópia "só nesta semana", para o editor dizer qual é. */
    fun observeWeekOnlyDay(templateId: Long): Flow<LocalDate?> = sessions.observeByTemplate(templateId).map { it?.date }

    suspend fun createTemplate(): Long {
        val used = templates.codes().toSet()
        val code = ('A'..'Z').map { it.toString() }.firstOrNull { it !in used } ?: "${used.size + 1}"
        return templates.insertTemplate(TemplateEntity(code = code, name = "Treino $code", updatedAt = clock()))
    }

    /**
     * Apaga um treino do plano base. Os dias do plano com ele viram descanso, os dias que vêm por aí sem começar
     * saem da semana e o histórico (treinos feitos) continua. Cópias "só nesta semana" não passam por aqui.
     * [tombstone] avisa o painel do treinador; o sync passa false quando a ordem de apagar veio de lá.
     */
    suspend fun deleteTemplate(templateId: Long, tombstone: Boolean = true): Boolean {
        val deleted = db.withTransaction {
            val template = templates.getTemplate(templateId)?.takeIf { !it.weekOnly } ?: return@withTransaction false
            val date = today()
            sessions.deleteUntouchedWithTemplate(date, templateId)
            // Dia fundido com o treino apagado fica só com o outro.
            sessions.getUpcomingWithTemplate(date, templateId).filter { it.mergedTemplateId == templateId }.forEach { s ->
                val main = s.templateId?.let { templates.getTemplate(it)?.name } ?: s.name
                sessions.update(s.copy(mergedTemplateId = null, name = main))
            }
            sessions.resolveMissedWithTemplate(templateId)
            plan.clearTemplate(templateId, clock())
            templates.deleteTemplate(templateId)
            if (tombstone) sync.addTombstones(listOf(TombstoneEntity(templateKey(template.remoteId), clock())))
            true
        }
        if (deleted) ensureWeek(WeekLogic.weekStartOf(today()))
        return deleted
    }

    suspend fun renameTemplate(templateId: Long, name: String) {
        val template = templates.getTemplate(templateId) ?: return
        val clean = name.trim()
        if (clean.isEmpty() || clean == template.name) return
        templates.updateTemplate(template.copy(name = clean, updatedAt = clock()))
        // Cópia de um dia só: o nome vale direto para aquele dia.
        if (template.weekOnly) sessions.renameForTemplate(templateId, clean) else renameUpcoming(templateId)
    }

    /**
     * Só o nome muda: os dias que vêm por aí ganham o nome novo sem refazer a semana (refazer apagaria
     * o que o recálculo decidiu). Num dia fundido, o nome junta os dois treinos de novo.
     */
    internal suspend fun renameUpcoming(templateId: Long) {
        sessions.getUpcomingWithTemplate(today(), templateId).forEach { session ->
            val main = session.templateId?.let { templates.getTemplate(it)?.name } ?: return@forEach
            val merged = session.mergedTemplateId?.let { templates.getTemplate(it)?.name }
            sessions.update(session.copy(name = if (merged == null) main else "$merged + $main"))
        }
    }

    suspend fun addExerciseToTemplate(templateId: Long, exerciseId: String) = db.withTransaction {
        val position = templates.maxPosition(templateId) + 1
        templates.insertItem(
            TemplateItemEntity(
                templateId = templateId, exerciseId = exerciseId, position = position,
                sets = 3, reps = 10, restSec = 90,
            )
        )
        templates.touch(listOf(templateId), clock())
    }

    suspend fun updateTemplateItem(itemId: Long, sets: Int, reps: Int) = db.withTransaction {
        templates.updateItemVolume(itemId, sets.coerceIn(1, 10), reps.coerceIn(1, 50))
        touchItem(itemId)
    }

    /** Descanso entre séries, de 0 a 10 min. */
    suspend fun setItemRest(itemId: Long, restSec: Int) = db.withTransaction {
        templates.updateItemRest(itemId, restSec.coerceIn(0, MAX_REST_SEC))
        touchItem(itemId)
    }

    suspend fun setItemNote(itemId: Long, note: String) = db.withTransaction {
        val clean = note.trim().take(MAX_TEXT)
        if (templates.getItem(itemId)?.note == clean) return@withTransaction
        templates.updateItemNote(itemId, clean)
        touchItem(itemId)
    }

    /** Sobe ([delta] < 0) ou desce o exercício na ordem do treino. */
    suspend fun moveTemplateItem(itemId: Long, delta: Int) = db.withTransaction {
        val item = templates.getItem(itemId) ?: return@withTransaction
        val items = templates.getItems(item.templateId).toMutableList()
        val from = items.indexOfFirst { it.id == itemId }
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (from < 0 || from == to) return@withTransaction
        items.add(to, items.removeAt(from))
        // Regrava todas as posições: as antigas podem ter buracos de exercícios removidos.
        items.forEachIndexed { index, it -> if (it.position != index) templates.updateItemPosition(it.id, index) }
        templates.touch(listOf(item.templateId), clock())
    }

    suspend fun removeTemplateItem(itemId: Long) = db.withTransaction {
        touchItem(itemId)
        templates.deleteItem(itemId)
    }

    private suspend fun touchItem(itemId: Long) {
        templates.getItem(itemId)?.let { templates.touch(listOf(it.templateId), clock()) }
    }

    /** Refaz as sessões de hoje em diante que ninguém começou, para refletir o plano novo. */
    internal suspend fun refreshUpcomingSessions() {
        val date = today()
        sessions.deleteUntouchedFrom(date)
        ensureWeek(WeekLogic.weekStartOf(date))
    }

    private fun suggestionFor(last: List<SetLogEntity>, targetReps: Int, targetSets: Int, equipment: String): LoadSuggestion? =
        Progression.suggest(last.map { DoneSet(it.weightKg, it.reps) }, targetReps, targetSets, equipment)

    private fun formatLastTime(last: List<SetLogEntity>): String? {
        if (last.isEmpty()) return null
        val weight = last.maxOf { it.weightKg }
        val reps = last.joinToString(", ") { it.reps.toString() }
        return if (weight > 0) "${formatKg(weight)} kg × $reps" else "$reps reps"
    }
}

const val MAX_REST_SEC = 600

/** Limite de observações e comentários: cabe na tela e no painel. */
const val MAX_TEXT = 300

fun templateKey(remoteId: String) = "template:$remoteId"

fun exerciseKey(exerciseId: String) = "exercise:$exerciseId"

/** Séries de cada exercício num treino fundido. */
fun mergedSets(sets: Int) = (sets - 1).coerceAtLeast(2)

/**
 * A data de hoje como fluxo: confere a cada minuto enquanto alguém observa e emite quando o dia vira.
 * Tela aberta de um dia para o outro (app em segundo plano à noite) passa sozinha para o dia novo.
 */
fun observeDate(today: () -> LocalDate): Flow<LocalDate> = flow {
    while (true) {
        emit(today())
        delay(60_000)
    }
}.distinctUntilChanged()

fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.1f".format(kg).replace('.', ',')
