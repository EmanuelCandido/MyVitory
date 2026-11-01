package com.myvitory.app.data.sync

import androidx.room.withTransaction
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.SentDayEntity
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.domain.sync.PlanMerge
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** O que uma rodada de sync fez. [weightsHash] é guardado para não reenviar as pesagens sem mudança. */
data class SyncReport(val plan: ImportResult, val daysSent: Int, val daysDeleted: Int, val weightsHash: Int?)

/**
 * Uma rodada de sync com o painel: junta o plano (os dois lados podem mudar), envia o histórico de treinos
 * que mudou e, se a pessoa quiser, as pesagens. O celular continua sendo a fonte da verdade do histórico.
 */
class SyncEngine(
    private val db: MyVitoryDatabase,
    training: TrainingRepository,
    private val remote: CoachRemote,
    private val appVersion: String,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val store = PlanSyncStore(db, training)
    private val history = HistoryExport(db, training)
    private val sync = db.sync()

    suspend fun run(shareWeight: Boolean, lastWeightsHash: Int?): SyncReport {
        val plan = syncPlan()
        val (sent, deleted) = syncDays()
        val weightsHash = syncWeights(shareWeight, lastWeightsHash)
        remote.markSynced(appVersion)
        return SyncReport(plan, sent, deleted, weightsHash)
    }

    suspend fun syncPlan(): ImportResult {
        val local = store.export()
        var merged = local
        remote.updatePlan { cloud ->
            merged = if (cloud == null) local else PlanMerge.merge(local, cloud, clock())
            merged.takeIf { it != cloud }
        }
        return store.import(merged, before = local)
    }

    /** Envia os dias da janela (últimos [HISTORY_DAYS] dias até o fim desta semana) que mudaram desde o último envio. */
    suspend fun syncDays(): Pair<Int, Int> {
        val date = today()
        val from = date.minusDays(HISTORY_DAYS)
        val to = WeekLogic.weekStartOf(date).plusDays(6)
        val docs = history.days(from, to)
        val sent = sync.sentDays().associate { it.date to it.hash }
        val upserts = docs.filter { (day, doc) -> sent[day] != doc.hashCode() }
        // Dia que sumiu do celular (ex.: virou descanso do plano) sai do painel também.
        val deletes = sent.keys.filter { !it.isBefore(from) && !it.isAfter(to) && it !in docs }
        if (upserts.isEmpty() && deletes.isEmpty()) return 0 to 0
        remote.pushDays(upserts, deletes)
        db.withTransaction {
            sync.markSent(upserts.map { (day, doc) -> SentDayEntity(day, doc.hashCode()) })
            if (deletes.isNotEmpty()) sync.forgetSent(deletes)
        }
        return upserts.size to deletes.size
    }

    private suspend fun syncWeights(share: Boolean, lastHash: Int?): Int? {
        if (!share) {
            if (lastHash != null) remote.pushWeights(null)
            return null
        }
        val points = db.weights().observeAll().first()
        val doc = mapOf("points" to points.map { mapOf("date" to it.date.toString(), "kg" to it.kg) })
        val hash = doc.hashCode()
        if (hash != lastHash) remote.pushWeights(doc)
        return hash
    }

    companion object {
        /** Quanto do histórico o painel mostra. */
        const val HISTORY_DAYS = 120L
    }
}
