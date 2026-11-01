package com.myvitory.app.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.myvitory.app.BuildConfig
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.sync.FirestoreCoachRemote.Companion.ATHLETES
import com.myvitory.app.data.sync.FirestoreCoachRemote.Companion.INVITES
import java.security.SecureRandom
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Treinador ligado à atleta, como aparece no app. */
data class Coach(val uid: String, val name: String, val email: String)

/** O que o app mostra da ficha da atleta na nuvem. */
data class AthleteCloud(val coaches: List<Coach>, val lastSyncAt: Long?)

/** Firebase configurado neste build (pelo local.properties) ou não. Sem configuração o painel fica escondido. */
object FirebaseSetup {
    val configured: Boolean get() = BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank()

    @Volatile private var app: FirebaseApp? = null

    fun app(context: Context): FirebaseApp? {
        if (!configured) return null
        app?.let { return it }
        synchronized(this) {
            app?.let { return it }
            val options = FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .build()
            val created = FirebaseApp.getApps(context).firstOrNull { it.name == NAME } ?: FirebaseApp.initializeApp(context, options, NAME)
            // Emulador local (só no debug): testa o painel e o app sem tocar no projeto de verdade.
            BuildConfig.FIREBASE_EMULATOR_HOST.takeIf { it.isNotBlank() }?.let { host ->
                FirebaseFirestore.getInstance(created).useEmulator(host, 8080)
                FirebaseAuth.getInstance(created).useEmulator(host, 9099)
            }
            app = created
            return created
        }
    }

    private const val NAME = "painel"
}

/**
 * Liga o celular ao painel: conta anônima (sem senha nem e-mail), ficha da atleta e código de pareamento.
 * O treinador digita o código no painel e passa a ver e editar o plano. Reinstalar o app gera outra conta:
 * é só conectar e parear de novo (o plano sobe do celular).
 */
class CoachLink(context: Context, private val db: MyVitoryDatabase, private val prefs: SyncPrefs) {
    private val firebase = FirebaseSetup.app(context)
    private val auth get() = FirebaseAuth.getInstance(firebase!!)
    val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance(firebase!!)

    val available: Boolean get() = firebase != null

    suspend fun connect(name: String): String {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: error("Não foi possível entrar")
        val clean = name.trim().ifEmpty { "Atleta" }.take(40)
        firestore.collection(ATHLETES).document(user.uid)
            .set(mapOf("ownerUid" to user.uid, "name" to clean, "createdAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
        prefs.connected(user.uid, clean)
        return user.uid
    }

    /** Novo código de 8 letras, válido por [PAIR_CODE_MINUTES] minutos. */
    suspend fun newPairCode(): String {
        val athleteId = prefs.current().athleteId ?: error("Conecte primeiro")
        val code = (1..8).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
        val expiresAt = System.currentTimeMillis() + PAIR_CODE_MINUTES * 60_000
        firestore.collection(INVITES).document(code).set(
            mapOf(
                "athleteId" to athleteId,
                "name" to prefs.current().name,
                "expiresAt" to Timestamp(Date(expiresAt)),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        prefs.pairCode(code, expiresAt)
        return code
    }

    /** Ficha da atleta ao vivo (treinadores ligados, último sync) enquanto alguém observa. */
    fun observeAthlete(athleteId: String): Flow<AthleteCloud?> = callbackFlow {
        val registration = firestore.collection(ATHLETES).document(athleteId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val coaches = (snapshot?.get("coaches") as? Map<String, Map<String, Any?>>).orEmpty().map { (uid, info) ->
                Coach(uid, info["name"] as? String ?: "Treinador", info["email"] as? String ?: "")
            }.sortedBy { it.name }
            trySend(AthleteCloud(coaches, snapshot?.getTimestamp("lastSyncAt")?.toDate()?.time))
        }
        awaitClose { registration.remove() }
    }

    suspend fun removeCoach(uid: String) {
        val athleteId = prefs.current().athleteId ?: return
        firestore.collection(ATHLETES).document(athleteId).update("coaches.$uid", FieldValue.delete()).await()
    }

    suspend fun setShareWeight(on: Boolean) {
        prefs.setShareWeight(on)
        prefs.current().athleteId?.let { id ->
            firestore.collection(ATHLETES).document(id).set(mapOf("shareWeight" to on), SetOptions.merge()).await()
        }
    }

    /** Desliga o painel e apaga tudo o que estava na nuvem. Os dados do celular ficam como estão. */
    suspend fun disconnectAndErase() {
        val state = prefs.current()
        state.athleteId?.let { id ->
            val athlete = firestore.collection(ATHLETES).document(id)
            val docs = athlete.collection("days").get().await().documents.map { it.reference } +
                listOf(athlete.collection("plan").document("current"), athlete.collection("private").document("weights"))
            docs.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
            state.pairCode?.let { runCatching { firestore.collection(INVITES).document(it).delete().await() } }
            athlete.delete().await()
            runCatching { auth.currentUser?.delete()?.await() }
        }
        auth.signOut()
        db.sync().clearSent()
        prefs.clear()
    }

    companion object {
        const val PAIR_CODE_MINUTES = 30L
        /** Sem 0/O e 1/I/L: o código é lido em voz alta ou de outra tela. */
        private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun formatCode(code: String) = if (code.length == 8) "${code.take(4)}-${code.drop(4)}" else code
    }
}
