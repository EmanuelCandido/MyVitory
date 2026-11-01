package com.myvitory.app.data.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myvitory.app.domain.sync.PlanSnapshot
import com.myvitory.app.domain.sync.SnapshotCodec
import java.time.LocalDate
import kotlinx.coroutines.tasks.await

/**
 * Onde cada coisa fica no Firestore (o mesmo que o painel e as regras em firebase/firestore.rules usam):
 *
 * - athletes/{athleteId}: nome, treinadores ligados e a hora do último sync
 * - athletes/{athleteId}/plan/current: o plano ([SnapshotCodec])
 * - athletes/{athleteId}/days/{aaaa-mm-dd}: um documento por dia de treino ([HistoryExport])
 * - athletes/{athleteId}/private/weights: as pesagens, só quando a pessoa compartilha
 */
class FirestoreCoachRemote(private val firestore: FirebaseFirestore, athleteId: String) : CoachRemote {
    private val athlete = firestore.collection(ATHLETES).document(athleteId)
    private val planDoc = athlete.collection("plan").document("current")

    override suspend fun updatePlan(decide: (cloud: PlanSnapshot?) -> PlanSnapshot?) {
        firestore.runTransaction { tx ->
            val snapshot = tx.get(planDoc)
            val cloud = snapshot.data?.let(SnapshotCodec::decode)
            decide(cloud)?.let { plan ->
                tx.set(planDoc, SnapshotCodec.encode(plan) + mapOf("updatedBy" to "app", "savedAt" to FieldValue.serverTimestamp()))
            }
            null
        }.await()
    }

    override suspend fun pushDays(upserts: Map<LocalDate, Map<String, Any?>>, deletes: List<LocalDate>) {
        val days = athlete.collection("days")
        // Documento null = apagar. Um lote do Firestore aceita até 500 escritas.
        val writes = upserts.entries.map { it.key to it.value } + deletes.map { it to null }
        writes.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (day, doc) ->
                val ref = days.document(day.toString())
                if (doc == null) batch.delete(ref) else batch.set(ref, doc)
            }
            batch.commit().await()
        }
    }

    override suspend fun pushWeights(doc: Map<String, Any?>?) {
        val ref = athlete.collection("private").document("weights")
        if (doc == null) ref.delete().await() else ref.set(doc + mapOf("savedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun markSynced(appVersion: String) {
        athlete.set(mapOf("lastSyncAt" to FieldValue.serverTimestamp(), "appVersion" to appVersion), SetOptions.merge()).await()
    }

    companion object {
        const val ATHLETES = "athletes"
        const val INVITES = "invites"
    }
}
