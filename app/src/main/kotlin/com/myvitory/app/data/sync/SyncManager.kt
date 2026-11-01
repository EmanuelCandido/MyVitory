package com.myvitory.app.data.sync

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.firestore.ListenerRegistration
import com.myvitory.app.BuildConfig
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class SyncOutcome { DONE, OFF, FAILED }

/**
 * Tudo do painel do treinador no app: conexão ([link]), rodadas de sync e os gatilhos (mudança local,
 * mudança na nuvem com o app aberto, e o [SyncWorker] periódico). Existe só quando o build tem Firebase.
 */
class SyncManager(
    private val context: Context,
    private val db: MyVitoryDatabase,
    private val training: TrainingRepository,
    val prefs: SyncPrefs,
    val link: CoachLink,
) {
    private val running = Mutex()

    val state: Flow<SyncState> = prefs.state

    /** Uma rodada completa. Seguro de chamar de vários lugares: uma de cada vez. */
    suspend fun runOnce(): SyncOutcome = running.withLock {
        val state = prefs.current()
        val athleteId = state.athleteId ?: return@withLock SyncOutcome.OFF
        try {
            val engine = SyncEngine(db, training, FirestoreCoachRemote(link.firestore, athleteId), BuildConfig.VERSION_NAME)
            val report = withContext(Dispatchers.IO) { engine.run(state.shareWeight, prefs.weightsHash()) }
            prefs.setWeightsHash(report.weightsHash)
            prefs.synced(System.currentTimeMillis())
            SyncWorker.notifyChanges(context, report.plan)
            SyncOutcome.DONE
        } catch (e: Exception) {
            prefs.failed(e.message ?: e.javaClass.simpleName)
            SyncOutcome.FAILED
        }
    }

    /** Liga os gatilhos. Chamado uma vez na abertura do app. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            prefs.state.map { it.connected }.distinctUntilChanged().collect { connected ->
                if (connected) {
                    SyncWorker.schedule(context)
                    SyncWorker.runNow(context)
                } else {
                    SyncWorker.cancel(context)
                }
            }
        }
        // Mudou algo no celular (plano, treino feito, pesagem): sincroniza depois de um tempo sem mexer.
        scope.launch {
            db.sync().observeChanges().drop(1).debounce(LOCAL_DEBOUNCE_MS).collect {
                if (prefs.current().connected) SyncWorker.runNow(context)
            }
        }
        // Com o app aberto, uma mudança do treinador chega na hora (sem esperar os 15 min).
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var registration: ListenerRegistration? = null
                try {
                    prefs.state.map { it.athleteId }.distinctUntilChanged().collect { athleteId ->
                        registration?.remove()
                        registration = athleteId?.let { listenToPlan(it) }
                    }
                } finally {
                    registration?.remove()
                }
            }
        }
    }

    private fun listenToPlan(athleteId: String): ListenerRegistration =
        link.firestore.collection(FirestoreCoachRemote.ATHLETES).document(athleteId).collection("plan").document("current")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null || snapshot.metadata.hasPendingWrites()) return@addSnapshotListener
                if (snapshot.getString("updatedBy") == "web") SyncWorker.runNow(context)
            }

    companion object {
        private const val LOCAL_DEBOUNCE_MS = 20_000L
    }
}
