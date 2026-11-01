package com.myvitory.app.data

import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.data.db.WeightEntity
import com.myvitory.app.domain.Weighing
import com.myvitory.app.domain.WeightLogic
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Pesagens: uma por dia, em qualquer data até hoje. */
class WeightRepository(
    db: MyVitoryDatabase,
    val today: () -> LocalDate = { LocalDate.now() },
) {
    private val dao = db.weights()

    fun observe(): Flow<List<Weighing>> = dao.observeAll().map { list -> list.map { Weighing(it.date, it.kg) } }

    suspend fun latest(): Weighing? = dao.latest()?.let { Weighing(it.date, it.kg) }

    suspend fun all(): List<Weighing> = observe().first()

    /**
     * Registra (ou corrige) o peso de um dia. Datas no futuro não valem.
     * Devolve a pesagem que existia naquele dia, para o "Desfazer".
     */
    suspend fun record(date: LocalDate, kg: Double): Weighing? {
        require(!date.isAfter(today())) { "data no futuro" }
        require(WeightLogic.isValidKg(kg)) { "peso fora da faixa" }
        val previous = dao.get(date)?.let { Weighing(it.date, it.kg) }
        dao.upsert(WeightEntity(date, Math.round(kg * 10) / 10.0))
        return previous
    }

    suspend fun delete(date: LocalDate) = dao.delete(date)

    /** Desfaz um [record] ou [delete]: volta o dia como era ([before] null = o dia não tinha pesagem). */
    suspend fun restore(date: LocalDate, before: Weighing?) {
        if (before == null) dao.delete(date) else dao.upsert(WeightEntity(before.date, before.kg))
    }
}
