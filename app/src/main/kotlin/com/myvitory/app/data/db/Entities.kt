package com.myvitory.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.SessionStatus
import java.time.LocalDate
import java.util.UUID

/** Id estável entre aparelhos (o painel do treinador usa este, não o id local do Room). */
fun newRemoteId(): String = UUID.randomUUID().toString().replace("-", "")

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equipment: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    /** Criado pelo usuário: pode ser editado e apagado. Os do catálogo não. */
    @ColumnInfo(defaultValue = "0") val custom: Boolean = false,
    /** Última mudança (ms). No sync com o painel, a versão mais recente vence. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

/** Um treino da divisão (A, B, C...). */
@Entity(tableName = "templates", indices = [Index(value = ["remoteId"], unique = true)])
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    /** Cópia feita para um dia só ("só nesta semana"): não aparece no plano base. */
    @ColumnInfo(defaultValue = "0") val weekOnly: Boolean = false,
    @ColumnInfo(defaultValue = "") val remoteId: String = newRemoteId(),
    /** Última mudança no treino ou nos exercícios dele (ms). No sync, a versão mais recente vence. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
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
    /** Observação de execução ("descer em 3 s"), mostrada no treino. */
    @ColumnInfo(defaultValue = "") val note: String = "",
)

/** Plano base: qual treino cai em cada dia da semana (1 = segunda ... 7 = domingo). Sem treino = descanso. */
@Entity(tableName = "plan_days")
data class PlanDayEntity(
    @PrimaryKey val dayOfWeek: Int,
    val templateId: Long?,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
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
    /** Treino fundido a este pelo recálculo: os dois no mesmo dia, com uma série a menos por exercício. */
    val mergedTemplateId: Long? = null,
    /** Mudado só para esta semana (pelo usuário ou por um recálculo aplicado): mudar o plano base não desfaz. */
    @ColumnInfo(defaultValue = "0") val special: Boolean = false,
    /** Como foi o treino, nas palavras da pessoa (aparece no painel do treinador). */
    val comment: String? = null,
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

/** Dia em que a pessoa avisou que não pode treinar (viagem, compromisso). O recálculo não usa esse dia. */
@Entity(tableName = "unavailable_days")
data class UnavailableDayEntity(@PrimaryKey val date: LocalDate)

/** Uma pesagem por dia, em kg. Pode ser de qualquer data (registrar uma pesagem antiga). */
@Entity(tableName = "weights")
data class WeightEntity(@PrimaryKey val date: LocalDate, val kg: Double)

/** Treino ou exercício apagado ("template:<remoteId>", "exercise:<id>"), para o sync não trazer de volta. */
@Entity(tableName = "sync_tombstones")
data class TombstoneEntity(@PrimaryKey val key: String, val deletedAt: Long)

/** Assinatura de cada dia já enviado ao painel: só o que mudou é reenviado. */
@Entity(tableName = "sync_sent_days")
data class SentDayEntity(@PrimaryKey val date: LocalDate, val hash: Int)
