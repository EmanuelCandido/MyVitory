package com.myvitory.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)
}

/** Item de treino já com o nome do exercício, para listas. */
data class TemplateItemRow(
    val id: Long,
    val templateId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val position: Int,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY code")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    fun observeTemplate(id: Long): Flow<TemplateEntity?>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getTemplate(id: Long): TemplateEntity?

    @Query("SELECT code FROM templates")
    suspend fun codes(): List<String>

    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: TemplateEntity)

    @Query(
        """SELECT i.id, i.templateId, i.exerciseId, e.name AS exerciseName, i.position, i.sets, i.reps, i.restSec
           FROM template_items i JOIN exercises e ON e.id = i.exerciseId
           WHERE i.templateId = :templateId ORDER BY i.position"""
    )
    fun observeItems(templateId: Long): Flow<List<TemplateItemRow>>

    @Query("SELECT * FROM template_items WHERE templateId = :templateId ORDER BY position")
    suspend fun getItems(templateId: Long): List<TemplateItemEntity>

    /** Quantidade de exercícios de cada treino, para as listas da semana. */
    @Query("SELECT templateId, COUNT(*) AS count FROM template_items GROUP BY templateId")
    fun observeItemCounts(): Flow<List<TemplateCount>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM template_items WHERE templateId = :templateId")
    suspend fun maxPosition(templateId: Long): Int

    @Insert
    suspend fun insertItem(item: TemplateItemEntity): Long

    @Query("UPDATE template_items SET sets = :sets, reps = :reps WHERE id = :itemId")
    suspend fun updateItemVolume(itemId: Long, sets: Int, reps: Int)

    @Query("DELETE FROM template_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    /** Troca "no plano todo": o treino passa a usar outro exercício na mesma posição. */
    @Query("UPDATE template_items SET exerciseId = :to WHERE templateId = :templateId AND exerciseId = :from")
    suspend fun replaceExercise(templateId: Long, from: String, to: String)

    @Query("SELECT DISTINCT exerciseId FROM template_items")
    fun observeExerciseIdsInPlans(): Flow<List<String>>
}

data class TemplateCount(val templateId: Long, val count: Int)

data class ExerciseHistoryPoint(val date: LocalDate, val topKg: Double, val volumeKg: Double, val reps: String)

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_days ORDER BY dayOfWeek")
    fun observePlan(): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days ORDER BY dayOfWeek")
    suspend fun getPlan(): List<PlanDayEntity>

    @Upsert
    suspend fun upsert(days: List<PlanDayEntity>)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE date = :date")
    suspend fun getByDate(date: LocalDate): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Sessões futuras que ninguém começou (e descansos decididos): refeitas quando o plano base muda. */
    @Query(
        """DELETE FROM sessions WHERE date >= :from AND status IN ('PLANNED', 'REST') AND startedAt IS NULL
           AND id NOT IN (SELECT DISTINCT sessionId FROM set_logs)"""
    )
    suspend fun deleteUntouchedFrom(from: LocalDate)

    /** Treinos perdidos sobre os quais o usuário ainda não decidiu. */
    @Query("SELECT * FROM sessions WHERE date BETWEEN :from AND :to AND status = 'MISSED' AND resolved = 0 ORDER BY date")
    fun observePendingMissed(from: LocalDate, to: LocalDate): Flow<List<SessionEntity>>

    /** Treinos feitos (total ou parcial) desde :from, para recuperação e histórico do recálculo. */
    @Query("SELECT * FROM sessions WHERE date >= :from AND status IN ('DONE', 'PARTIAL') ORDER BY date")
    fun observeTrainedSince(from: LocalDate): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE date >= :from AND status IN ('DONE', 'PARTIAL') ORDER BY date")
    suspend fun getTrainedSince(from: LocalDate): List<SessionEntity>
}

@Dao
interface SetLogDao {
    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY position, setIndex")
    fun observeForSession(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT COUNT(*) FROM set_logs WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query("SELECT DISTINCT exerciseId FROM set_logs WHERE sessionId = :sessionId AND done = 1")
    suspend fun doneExerciseIds(sessionId: Long): List<String>

    /** Um ponto por treino com o exercício: maior carga, volume e repetições feitas, do mais recente ao mais antigo. */
    @Query(
        """SELECT s.date AS date, MAX(l.weightKg) AS topKg, SUM(l.weightKg * l.reps) AS volumeKg,
                  GROUP_CONCAT(l.reps, ', ') AS reps
           FROM set_logs l JOIN sessions s ON s.id = l.sessionId
           WHERE l.exerciseId = :exerciseId AND l.done = 1
           GROUP BY s.id ORDER BY s.date DESC LIMIT :limit"""
    )
    fun observeHistory(exerciseId: String, limit: Int = 16): Flow<List<ExerciseHistoryPoint>>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId")
    suspend fun getForSession(sessionId: Long): List<SetLogEntity>

    @Insert
    suspend fun insertAll(logs: List<SetLogEntity>): List<Long>

    @Update
    suspend fun update(log: SetLogEntity)

    @Query("DELETE FROM set_logs WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun delete(id: Long)

    /** Séries feitas do exercício no treino mais recente antes de :beforeDate. Alimenta "Última vez". */
    @Query(
        """SELECT l.* FROM set_logs l JOIN sessions s ON s.id = l.sessionId
           WHERE l.exerciseId = :exerciseId AND l.done = 1 AND s.date = (
               SELECT MAX(s2.date) FROM set_logs l2 JOIN sessions s2 ON s2.id = l2.sessionId
               WHERE l2.exerciseId = :exerciseId AND l2.done = 1 AND s2.date < :beforeDate
           )
           ORDER BY l.setIndex"""
    )
    suspend fun lastDone(exerciseId: String, beforeDate: LocalDate): List<SetLogEntity>
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_cache WHERE query = :query ORDER BY position")
    suspend fun cached(query: String): List<CachedVideoEntity>

    @Query("DELETE FROM video_cache WHERE query = :query")
    suspend fun deleteQuery(query: String)

    @Insert
    suspend fun insertAll(videos: List<CachedVideoEntity>)

    @Query("SELECT * FROM pinned_videos WHERE exerciseId = :exerciseId")
    fun observePinned(exerciseId: String): Flow<PinnedVideoEntity?>

    @Upsert
    suspend fun pin(video: PinnedVideoEntity)

    @Query("DELETE FROM pinned_videos WHERE exerciseId = :exerciseId")
    suspend fun unpin(exerciseId: String)
}

@Dao
interface AvailabilityDao {
    @Query("SELECT * FROM unavailable_days WHERE date BETWEEN :from AND :to")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<UnavailableDayEntity>>

    @Query("SELECT * FROM unavailable_days WHERE date BETWEEN :from AND :to")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<UnavailableDayEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(day: UnavailableDayEntity)

    @Query("DELETE FROM unavailable_days WHERE date = :date")
    suspend fun remove(date: LocalDate)
}
