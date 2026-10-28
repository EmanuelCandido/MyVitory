package com.myvitory.app.domain.planner

import com.myvitory.app.domain.Muscle
import com.myvitory.app.domain.MuscleLoads
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Um treino que ainda pode ser colocado na semana (perdido ou ainda por fazer). */
data class Workout(
    val sessionId: Long,
    val templateId: Long,
    val name: String,
    val loads: MuscleLoads,
    val originalDate: LocalDate,
    val missed: Boolean,
)

/** Um treino com data fixa: já feito (histórico) ou do plano da semana seguinte. */
data class Training(val date: LocalDate, val name: String, val loads: MuscleLoads)

/** Um dia livre para receber treino. [preferredRest]: no plano base é descanso. */
data class Slot(val date: LocalDate, val preferredRest: Boolean)

data class PlannerInput(
    val pending: List<Workout>,
    val slots: List<Slot>,
    val history: List<Training>,
    val nextWeek: List<Training>,
    val maxConsecutive: Int = 3,
    val recoveryHours: (Muscle) -> Int = { it.recoveryHours },
)

data class Proposal(val assignment: Map<LocalDate, Workout>, val dropped: List<Workout>, val cost: Double) {
    fun dateOf(workout: Workout): LocalDate? = assignment.entries.firstOrNull { it.value.sessionId == workout.sessionId }?.key
}

/** Resultado do recálculo: a melhor proposta e, quando faz sentido, uma alternativa de outro tipo. */
data class ReplanResult(val best: Proposal, val alternative: Proposal?)

/**
 * Motor de recálculo (prancheta "Motor de recálculo").
 *
 * A semana tem no máximo 7 dias e 7 treinos, então dá para testar todas as distribuições
 * (cada treino num dia livre diferente ou fora da semana) e escolher a de menor custo.
 * Descanso entre treinos do mesmo músculo é custo alto, não regra rígida: muitas divisões
 * reais já repetem músculos com folga curta, e o plano original não pode virar "inválido".
 */
object Planner {
    const val SHORTFALL_PER_HOUR = 2.0
    const val MOVE_PER_DAY = 3.0
    const val REST_DAY = 25.0
    const val STREAK_PER_DAY = 30.0
    const val DROP_BASE = 100.0
    const val DROP_PER_LOAD_DAY = 10.0

    fun replan(input: PlannerInput): ReplanResult {
        val slots = input.slots.sortedBy { it.date }
        val restDates = slots.filter { it.preferredRest }.map { it.date }.toSet()
        var best: Proposal? = null
        var bestNoRest: Proposal? = null
        val bestByMissedPlacement = mutableMapOf<List<LocalDate?>, Proposal>()
        val missed = input.pending.filter { it.missed }

        val assignment = LinkedHashMap<LocalDate, Workout>()
        fun visit(index: Int) {
            if (index == input.pending.size) {
                val proposal = Proposal(LinkedHashMap(assignment), input.pending.filter { w -> assignment.values.none { it.sessionId == w.sessionId } }, cost(input, assignment))
                if (best == null || proposal.cost < best!!.cost) best = proposal
                if (assignment.keys.none { it in restDates } && (bestNoRest == null || proposal.cost < bestNoRest!!.cost)) bestNoRest = proposal
                val signature = missed.map { proposal.dateOf(it) }
                val current = bestByMissedPlacement[signature]
                if (current == null || proposal.cost < current.cost) bestByMissedPlacement[signature] = proposal
                return
            }
            val workout = input.pending[index]
            for (slot in slots) {
                if (slot.date in assignment) continue
                assignment[slot.date] = workout
                visit(index + 1)
                assignment.remove(slot.date)
            }
            visit(index + 1) // fora da semana
        }
        visit(0)

        val chosen = best!!
        val chosenSignature = missed.map { chosen.dateOf(it) }
        val alternative = when {
            chosen.assignment.keys.any { it in restDates } && bestNoRest != null && bestNoRest != chosen -> bestNoRest
            else -> bestByMissedPlacement.filterKeys { it != chosenSignature }.values.minByOrNull { it.cost }
        }
        return ReplanResult(chosen, alternative)
    }

    fun cost(input: PlannerInput, assignment: Map<LocalDate, Workout>): Double {
        val restDates = input.slots.filter { it.preferredRest }.map { it.date }.toSet()
        var total = 0.0
        val placed = assignment.values.map { it.sessionId }.toSet()
        input.pending.filter { it.sessionId !in placed }.forEach { total += dropCost(it, input) }
        assignment.forEach { (date, workout) ->
            total += abs(daysBetween(workout.originalDate, date)) * MOVE_PER_DAY
            if (date in restDates) total += REST_DAY
        }
        val trainings = input.history + assignment.map { (date, w) -> Training(date, w.name, w.loads) } + input.nextWeek
        total += recoveryShortfallHours(trainings, input.recoveryHours) * SHORTFALL_PER_HOUR
        val trainedDays = (input.history.map { it.date } + assignment.keys).toSet()
        total += maxOf(0, longestStreak(trainedDays) - input.maxConsecutive) * STREAK_PER_DAY
        return total
    }

    fun dropCost(workout: Workout, input: PlannerInput): Double =
        DROP_BASE + workout.loads.entries.sumOf { (m, load) -> input.recoveryHours(m) * load } / 24.0 * DROP_PER_LOAD_DAY

    /** Soma, para cada treino, quantas horas faltaram de descanso desde o treino anterior do mesmo músculo. */
    fun recoveryShortfallHours(trainings: List<Training>, recoveryHours: (Muscle) -> Int): Double {
        val sorted = trainings.sortedBy { it.date }
        val last = mutableMapOf<Muscle, Pair<LocalDate, Double>>()
        var shortfall = 0.0
        sorted.forEach { t ->
            t.loads.forEach { (m, load) ->
                last[m]?.let { (prevDate, prevLoad) ->
                    val gap = daysBetween(prevDate, t.date) * 24.0
                    val required = recoveryHours(m) * minOf(load, prevLoad)
                    if (gap < required) shortfall += required - gap
                }
                last[m] = t.date to load
            }
        }
        return shortfall
    }

    fun longestStreak(days: Set<LocalDate>): Int {
        var best = 0
        days.forEach { d ->
            if (d.minusDays(1) in days) return@forEach
            var run = 1
            while (d.plusDays(run.toLong()) in days) run++
            best = maxOf(best, run)
        }
        return best
    }

    // ---- Explicação de cada mudança ----

    /** Por que o treino ficou nesse dia: o músculo principal com o treino mais recente. */
    data class Reason(val muscle: Muscle?, val daysSince: Int?)

    data class DayChange(
        val date: LocalDate,
        val before: Workout?,
        val after: Workout?,
        val reason: Reason?,
        val usesRestDay: Boolean,
    )

    /** Primeiro treino da semana seguinte e a menor folga que ele terá, por músculo. */
    data class NextRest(val training: Training, val muscle: Muscle?, val gapHours: Int?, val requiredHours: Int?) {
        val ok get() = gapHours == null || requiredHours == null || gapHours >= requiredHours
    }

    data class Explanation(val changes: List<DayChange>, val dropped: List<Workout>, val nextRest: NextRest?)

    fun explain(input: PlannerInput, proposal: Proposal): Explanation {
        val restDates = input.slots.filter { it.preferredRest }.map { it.date }.toSet()
        val changes = input.slots.sortedBy { it.date }.mapNotNull { slot ->
            val before = input.pending.firstOrNull { !it.missed && it.originalDate == slot.date }
            val after = proposal.assignment[slot.date]
            if (before?.sessionId == after?.sessionId) return@mapNotNull null
            DayChange(
                date = slot.date,
                before = before,
                after = after,
                reason = after?.let { reasonFor(it, slot.date, input, proposal) },
                usesRestDay = after != null && slot.date in restDates,
            )
        }
        return Explanation(changes, proposal.dropped, nextRest(input, proposal))
    }

    private fun reasonFor(workout: Workout, date: LocalDate, input: PlannerInput, proposal: Proposal): Reason {
        val before = input.history + proposal.assignment.filterKeys { it < date }.map { (d, w) -> Training(d, w.name, w.loads) }
        val primaries = workout.loads.filterValues { it >= 1.0 }.keys
        val closest = primaries.mapNotNull { m ->
            before.filter { m in it.loads }.maxByOrNull { it.date }?.let { m to daysBetween(it.date, date).toInt() }
        }.minByOrNull { it.second }
        return if (closest == null) Reason(null, null) else Reason(closest.first, closest.second)
    }

    private fun nextRest(input: PlannerInput, proposal: Proposal): NextRest? {
        val first = input.nextWeek.minByOrNull { it.date } ?: return null
        val week = input.history + proposal.assignment.map { (d, w) -> Training(d, w.name, w.loads) }
        var worst: NextRest? = null
        first.loads.forEach { (m, load) ->
            val previous = week.filter { m in it.loads }.maxByOrNull { it.date } ?: return@forEach
            val gap = (daysBetween(previous.date, first.date) * 24).toInt()
            val required = (input.recoveryHours(m) * minOf(load, previous.loads.getValue(m))).toInt()
            val candidate = NextRest(first, m, gap, required)
            if (worst == null || gap - required < worst!!.gapHours!! - worst!!.requiredHours!!) worst = candidate
        }
        return worst ?: NextRest(first, null, null, null)
    }

    private fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}
