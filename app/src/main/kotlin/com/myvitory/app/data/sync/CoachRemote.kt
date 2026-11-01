package com.myvitory.app.data.sync

import com.myvitory.app.domain.sync.PlanSnapshot
import java.time.LocalDate

/**
 * A nuvem do painel do treinador vista pelo sync. Uma implementação usa o Firestore ([FirestoreCoachRemote]);
 * os testes usam uma em memória.
 */
interface CoachRemote {
    /**
     * Lê o plano da nuvem (null se ainda não existe) e grava o que [decide] devolver, na mesma transação.
     * [decide] pode rodar mais de uma vez (o Firestore repete a transação quando alguém escreveu no meio)
     * e devolve null quando não há nada para gravar.
     */
    suspend fun updatePlan(decide: (cloud: PlanSnapshot?) -> PlanSnapshot?)

    /** Grava os dias mudados e apaga os que deixaram de existir no celular. */
    suspend fun pushDays(upserts: Map<LocalDate, Map<String, Any?>>, deletes: List<LocalDate>)

    /** Pesagens compartilhadas; null apaga da nuvem (a pessoa desligou o compartilhamento). */
    suspend fun pushWeights(doc: Map<String, Any?>?)

    /** Registra que o celular sincronizou agora (o painel mostra "sincronizado há X min"). */
    suspend fun markSynced(appVersion: String)
}
