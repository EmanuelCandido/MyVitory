package com.myvitory.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.SessionStatus
import java.time.LocalDate

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equipment: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
)

/** Um treino da divisão (A, B, C...). */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
)

@Entity(
    tableName = "template_items",
    foreignKeys = [
        ForeignKey(TemplateEntity::class, ["id"], ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"]),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: String,
    val position: Int,
    val sets: Int,
    val reps: Int,
    val restSec: Int,
)

/** Plano base: qual treino cai em cada dia da semana (1 = segunda ... 7 = domingo). Sem treino = descanso. */
@Entity(tableName = "plan_days")
data class PlanDayEntity(
    @PrimaryKey val dayOfWeek: Int,
    val templateId: Long?,
)

/** Um treino (ou um descanso decidido) em uma data. Uma linha por dia. */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["date"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val templateId: Long?,
    val name: String,
    val status: SessionStatus,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    /** Treino perdido que o recálculo levou para outro dia. */
    val movedTo: LocalDate? = null,
    /** O usuário já decidiu o que fazer com este treino perdido (aplicou ou pulou o recálculo). */
    @ColumnInfo(defaultValue = "0") val resolved: Boolean = false,
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"]),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    /** Ordem do exercício dentro do treino. */
    val position: Int,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val restSec: Int,
    val done: Boolean = false,
)

/** Resultado de busca no YouTube guardado por 7 dias: cada busca gasta 100 unidades da cota diária. */
@Entity(tableName = "video_cache", indices = [Index("query")])
data class CachedVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val position: Int,
    val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val durationSec: Int?,
    val fetchedAt: Long,
)

/** Vídeo escolhido pelo usuário para um exercício: toca primeiro e funciona sem chave da API. */
@Entity(tableName = "pinned_videos")
data class PinnedVideoEntity(
    @PrimaryKey val exerciseId: String,
    val videoId: String,
    val title: String,
    val channel: String,
)
