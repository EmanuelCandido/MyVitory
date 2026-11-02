package com.myvitory.app.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myvitory.app.data.CoachRepository
import com.myvitory.app.data.TrainingRepository
import com.myvitory.app.data.db.MyVitoryDatabase
import com.myvitory.app.domain.SessionStatus
import com.myvitory.app.domain.WeekLogic
import com.myvitory.app.domain.sync.PlanSnapshot
import com.myvitory.app.domain.sync.SnapshotDate
import com.myvitory.app.domain.sync.SnapshotDay
import com.myvitory.app.domain.sync.SnapshotExercise
import com.myvitory.app.domain.sync.SnapshotItem
import com.myvitory.app.domain.sync.SnapshotTemplate
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A nuvem em memória: guarda o que o sync gravaria no Firestore e conta as escritas. */
class FakeRemote : CoachRemote {
    var plan: PlanSnapshot? = null
    var planWrites = 0
    val days = mutableMapOf<LocalDate, Map<String, Any?>>()
    var dayWrites = 0
    var weights: Map<String, Any?>? = null
    var syncs = 0

    override suspend fun updatePlan(decide: (cloud: PlanSnapshot?) -> PlanSnapshot?) {
        decide(plan)?.let {
            plan = it
            planWrites++
        }
    }

    override suspend fun pushDays(upserts: Map<LocalDate, Map<String, Any?>>, deletes: List<LocalDate>) {
        days.putAll(upserts)
        deletes.forEach { days.remove(it) }
        dayWrites += upserts.size + deletes.size
    }

    override suspend fun pushWeights(doc: Map<String, Any?>?) {
        weights = doc
    }

    override suspend fun markSynced(appVersion: String) {
        syncs++
    }

    /** O que o painel faz: muda o plano na nuvem. */
    fun edit(change: (PlanSnapshot) -> PlanSnapshot) {
        plan = change(plan!!)
    }
}

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private val monday = LocalDate.of(2026, 10, 26)
    private var today = monday.plusDays(2) // quarta
    private var now = 1_000L

    private lateinit var db: MyVitoryDatabase
    private lateinit var training: TrainingRepository
    private lateinit var remote: FakeRemote
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MyVitoryDatabase::class.java).build()
        training = TrainingRepository(db, today = { today }, clock = { now })
        TrainingRepository(db, today = { monday }).seedIfEmpty()
        training.ensureWeek(monday)
        remote = FakeRemote()
        engine = SyncEngine(db, training, remote, "teste", today = { today }, clock = { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun templateNamed(name: String) = db.templates().getBaseTemplates().first { it.name == name }

    private suspend fun dayName(date: LocalDate) = training.observeWeek(WeekLogic.weekStartOf(date)).first().first { it.date == date }.session?.name

    @Test
    fun `primeiro sync sobe o plano do celular e os dias da semana`() = runBlocking {
        engine.run(shareWeight = false, lastWeightsHash = null)

        val cloud = remote.plan!!
        assertEquals(5, cloud.templates.size)
        val legs = templateNamed("Pernas")
        assertEquals(legs.remoteId, cloud.week[3]!!.templateId)
        assertEquals(listOf("agachamento_livre", "leg_press_45", "cadeira_extensora", "mesa_flexora", "panturrilha_em_pe"), cloud.templates.getValue(legs.remoteId).items.map { it.exerciseId })
        // Segunda a sexta desta semana já existem no celular e vão para o painel.
        assertEquals(5, remote.days.size)
        assertEquals("Pernas", remote.days.getValue(monday.plusDays(2))["name"])
        assertEquals(1, remote.syncs)
    }

    @Test
    fun `sem mudança não escreve de novo`() = runBlocking {
        engine.run(false, null)
        val planWrites = remote.planWrites
        val dayWrites = remote.dayWrites

        val result = engine.run(false, null)

        assertTrue(result.plan.isEmpty)
        assertEquals(planWrites, remote.planWrites)
        assertEquals(dayWrites, remote.dayWrites)
    }

    @Test
    fun `treino mudado no painel chega ao celular com ordem, descanso e observação`() = runBlocking {
        engine.run(false, null)
        val legs = templateNamed("Pernas")
        remote.edit { plan ->
            val t = plan.templates.getValue(legs.remoteId)
            val items = listOf(SnapshotItem("leg_press_45", 4, 12, 75, "Pés no meio da plataforma")) + t.items.filter { it.exerciseId != "leg_press_45" }
            plan.copy(templates = plan.templates + (legs.remoteId to t.copy(name = "Pernas (quadríceps)", items = items, updatedAt = 5_000)))
        }

        val result = engine.syncPlan()

        assertEquals(listOf("Pernas (quadríceps)"), result.changedTemplates)
        val items = db.templates().getItems(legs.id)
        assertEquals("leg_press_45", items.first().exerciseId)
        assertEquals(75, items.first().restSec)
        assertEquals("Pés no meio da plataforma", items.first().note)
        assertEquals(listOf(0, 1, 2, 3, 4), items.map { it.position })
        // O dia de hoje (quarta, ainda não começado) ganha o nome novo.
        assertEquals("Pernas (quadríceps)", dayName(today))
    }

    @Test
    fun `treino novo do painel com exercício próprio entra no sábado`() = runBlocking {
        engine.run(false, null)
        remote.edit { plan ->
            plan.copy(
                exercises = plan.exercises + ("custom_painel" to SnapshotExercise("Coice na polia", "Polia", listOf("GLUTES"), emptyList(), 4_000)),
                templates = plan.templates + ("novo" to SnapshotTemplate("F", "Glúteos extra", 4_000, listOf(SnapshotItem("custom_painel", 3, 15, 45), SnapshotItem("nao_existe_no_app", 3, 10, 60)))),
                week = plan.week + (6 to SnapshotDay("novo", 4_000)),
            )
        }

        val result = engine.syncPlan()

        assertTrue(result.weekChanged)
        assertEquals(1, result.exercisesChanged)
        val created = templateNamed("Glúteos extra")
        // Exercício que o app não conhece fica de fora; o resto entra.
        assertEquals(listOf("custom_painel"), db.templates().getItems(created.id).map { it.exerciseId })
        assertTrue(db.exercises().get("custom_painel")!!.custom)
        assertEquals("Glúteos extra", dayName(monday.plusDays(5)))
    }

    @Test
    fun `treino apagado no painel some do plano e dos próximos dias, mas o feito continua`() = runBlocking {
        val back = templateNamed("Costas + Bíceps")
        val tuesday = monday.plusDays(1)
        training.markSession(training.observeWeek(monday).first().first { it.date == tuesday }.session!!.id, SessionStatus.DONE)
        engine.run(false, null)
        remote.edit { plan ->
            plan.copy(
                templates = plan.templates - back.remoteId,
                deleted = plan.deleted + ("template:${back.remoteId}" to 6_000),
            )
        }
        // Na semana que vem, terça seria Costas.
        today = monday.plusDays(7)
        training.ensureWeek(today)

        val result = engine.syncPlan()

        assertEquals(listOf("Costas + Bíceps"), result.deletedTemplates)
        assertTrue(db.templates().getBaseTemplates().none { it.remoteId == back.remoteId })
        assertNull(db.plan().getPlan().first { it.dayOfWeek == DayOfWeek.TUESDAY.value }.templateId)
        assertNull(dayName(today.plusDays(1)))
        // Terça passada (feita) continua no histórico.
        assertEquals("Costas + Bíceps", dayName(tuesday))
        // Celular e nuvem concordam: o próximo sync não muda nada.
        assertTrue(engine.syncPlan().isEmpty)
    }

    @Test
    fun `mudança no celular e no painel em treinos diferentes ficam as duas`() = runBlocking {
        engine.run(false, null)
        val chest = templateNamed("Peito + Tríceps")
        val shoulders = templateNamed("Ombros + Abdômen")
        now = 7_000
        training.renameTemplate(chest.id, "Peito pesado")
        remote.edit { plan ->
            val t = plan.templates.getValue(shoulders.remoteId)
            plan.copy(templates = plan.templates + (shoulders.remoteId to t.copy(name = "Ombros leve", updatedAt = 6_000)))
        }

        engine.syncPlan()

        assertEquals("Peito pesado", remote.plan!!.templates.getValue(chest.remoteId).name)
        assertEquals("Ombros leve", db.templates().getTemplate(shoulders.id)!!.name)
    }

    @Test
    fun `no mesmo treino vence quem mudou por último`() = runBlocking {
        engine.run(false, null)
        val chest = templateNamed("Peito + Tríceps")
        now = 7_000
        training.renameTemplate(chest.id, "Peito do celular")
        remote.edit { plan ->
            val t = plan.templates.getValue(chest.remoteId)
            plan.copy(templates = plan.templates + (chest.remoteId to t.copy(name = "Peito do painel", updatedAt = 8_000)))
        }

        engine.syncPlan()

        assertEquals("Peito do painel", db.templates().getTemplate(chest.id)!!.name)
        assertEquals("Peito do painel", remote.plan!!.templates.getValue(chest.remoteId).name)
    }

    @Test
    fun `treino apagado no celular é apagado na nuvem`() = runBlocking {
        engine.run(false, null)
        val glutes = templateNamed("Posterior + Glúteos")
        now = 9_000

        assertTrue(training.deleteTemplate(glutes.id))
        engine.syncPlan()

        val cloud = remote.plan!!
        assertFalse(glutes.remoteId in cloud.templates)
        assertEquals(9_000L, cloud.deleted["template:${glutes.remoteId}"])
        assertNull(cloud.week[5]!!.templateId)
    }

    @Test
    fun `treino feito vai com cargas e comentário, e peso só quando compartilhado`() = runBlocking {
        val session = training.observeWeek(monday).first().first { it.date == today }.session!!
        training.startWorkout(session.id)
        training.markSession(session.id, SessionStatus.DONE)
        training.setSessionComment(session.id, "  Senti o joelho  ")
        db.weights().upsert(com.myvitory.app.data.db.WeightEntity(today, 61.5))

        val report = engine.run(shareWeight = false, lastWeightsHash = null)
        assertNull(remote.weights)

        val doc = remote.days.getValue(today)
        assertEquals("DONE", doc["status"])
        assertEquals("Senti o joelho", doc["comment"])
        @Suppress("UNCHECKED_CAST")
        val exercises = doc["exercises"] as List<Map<String, Any?>>
        assertEquals(5, exercises.size)
        @Suppress("UNCHECKED_CAST")
        assertTrue((exercises.first()["sets"] as List<Map<String, Any?>>).all { it["done"] == true })

        val shared = engine.run(shareWeight = true, lastWeightsHash = report.weightsHash)
        assertEquals(listOf(mapOf("date" to today.toString(), "kg" to 61.5)), remote.weights!!["points"])
        // Desligar apaga da nuvem.
        engine.run(shareWeight = false, lastWeightsHash = shared.weightsHash)
        assertNull(remote.weights)
    }

    private suspend fun day(date: LocalDate) = training.observeWeek(WeekLogic.weekStartOf(date)).first().first { it.date == date }

    @Test
    fun `feriado e domingo fechado no painel tiram o treino do dia e o recálculo não usa esses dias`() = runBlocking {
        engine.run(false, null)
        val friday = monday.plusDays(4)
        val sunday = monday.plusDays(6)
        remote.edit { plan ->
            plan.copy(
                week = plan.week + (7 to SnapshotDay(null, 5_000, closed = true)),
                dates = mapOf(friday.toString() to SnapshotDate(true, "Feriado municipal", false, null, 5_000)),
            )
        }

        val result = engine.syncPlan()

        assertEquals(1, result.datesChanged)
        assertEquals(SessionStatus.MISSED, day(friday).session?.status)
        assertEquals("Feriado municipal", day(friday).closedLabel)
        assertEquals("Academia fechada", day(sunday).closedLabel)
        assertTrue(day(sunday).unavailable)
        val replan = CoachRepository(db, today = { today }, clock = { now }).buildReplan()!!
        val slots = replan.input.slots.map { it.date }
        assertFalse(friday in slots)
        assertFalse(sunday in slots)
        assertTrue(monday.plusDays(5) in slots) // sábado continua livre
        assertTrue(replan.result.best.assignment.keys.none { it == friday || it == sunday })

        // O feriado saiu do calendário: o treino volta para sexta.
        remote.edit { plan -> plan.copy(dates = mapOf(friday.toString() to SnapshotDate(false, "", false, null, 6_000))) }
        engine.syncPlan()
        assertEquals(SessionStatus.PLANNED, day(friday).session?.status)
        assertNull(day(friday).closedLabel)
    }

    @Test
    fun `treino escolhido para uma data vale só naquele dia, inclusive na semana que vem`() = runBlocking {
        engine.run(false, null)
        val thursday = monday.plusDays(3)
        val nextTuesday = monday.plusDays(8)
        val legs = templateNamed("Pernas")
        remote.edit { plan ->
            plan.copy(
                dates = mapOf(
                    thursday.toString() to SnapshotDate(false, "", true, legs.remoteId, 5_000),
                    nextTuesday.toString() to SnapshotDate(false, "", true, null, 5_000),
                ),
            )
        }

        engine.syncPlan()

        assertEquals("Pernas", day(thursday).session?.name)
        assertTrue(day(thursday).special)
        training.ensureWeek(monday.plusWeeks(1))
        assertNull(day(nextTuesday).session) // descanso escolhido
        assertTrue(day(nextTuesday).special)
        assertEquals("Pernas", day(monday.plusDays(9)).session?.name) // quarta seguinte segue o plano

        // Desfeito no painel: quinta volta ao plano base.
        remote.edit { plan -> plan.copy(dates = plan.dates + (thursday.toString() to SnapshotDate(false, "", false, null, 6_000))) }
        engine.syncPlan()
        assertEquals(db.plan().getPlan().first { it.dayOfWeek == 4 }.templateId, day(thursday).session?.templateId)
        assertFalse(day(thursday).special)
    }

    @Test
    fun `mudar o treino de um dia no celular não abre a academia fechada`() = runBlocking {
        engine.run(false, null)
        remote.edit { plan -> plan.copy(week = plan.week + (7 to SnapshotDay(null, 5_000, closed = true))) }
        engine.syncPlan()

        training.setPlanDay(DayOfWeek.SUNDAY, templateNamed("Pernas").id)
        engine.syncPlan()

        assertTrue(remote.plan!!.week.getValue(7).closed)
        // Treino num dia fechado vai direto para o recálculo.
        assertEquals(SessionStatus.MISSED, day(monday.plusDays(6)).session?.status)
    }
}
