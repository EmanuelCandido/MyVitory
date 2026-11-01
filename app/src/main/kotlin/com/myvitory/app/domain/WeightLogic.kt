package com.myvitory.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Uma pesagem. */
data class Weighing(val date: LocalDate, val kg: Double)

/** Meta de peso: [startKg] é o peso de quando a meta foi definida (base da barra de progresso). */
data class WeightGoal(val targetKg: Double, val startKg: Double, val by: LocalDate? = null) {
    val losing get() = targetKg < startKg
}

/** Ritmo atual, pela reta dos mínimos quadrados das pesagens recentes. */
data class WeightTrend(val kgPerWeek: Double, val from: LocalDate, val to: LocalDate, val count: Int) {
    val kgPerDay get() = kgPerWeek / 7
}

data class WeightForecast(val date: LocalDate, val kg: Double)

data class GoalProgress(
    val goal: WeightGoal,
    val currentKg: Double,
    /** Quanto falta, sempre positivo; zero quando a meta foi alcançada. */
    val remainingKg: Double,
    /** 0 a 1, do peso inicial até a meta. */
    val fraction: Double,
    val reached: Boolean,
    /** Quando chega na meta no ritmo atual. Null se o ritmo vai na direção contrária, é parado ou passa de 2 anos. */
    val eta: LocalDate?,
    /** Ritmo necessário para chegar até [WeightGoal.by], em kg por semana (negativo = perder). */
    val neededKgPerWeek: Double?,
)

object WeightLogic {
    /** Pesar pelo menos uma vez por mês. */
    const val REMIND_AFTER_DAYS = 30
    /** A tendência olha só os últimos 90 dias: o ritmo de um ano atrás não diz nada sobre o próximo mês. */
    const val TREND_WINDOW_DAYS = 90
    /** Pesagens a menos de uma semana de distância são ruído (água, refeição) e não viram tendência. */
    const val MIN_TREND_SPAN_DAYS = 7
    const val FORECAST_DAYS = 30
    /** Última pesagem mais velha que isso: prever o mês seguinte seria chute. */
    const val MAX_FORECAST_GAP_DAYS = 60
    const val MIN_KG = 20.0
    const val MAX_KG = 350.0

    fun isValidKg(kg: Double) = kg in MIN_KG..MAX_KG

    fun daysSinceLast(weights: List<Weighing>, today: LocalDate): Long? =
        weights.maxByOrNull { it.date }?.let { ChronoUnit.DAYS.between(it.date, today) }

    /** Sem pesagem ou com a última há 30 dias ou mais. */
    fun isDue(weights: List<Weighing>, today: LocalDate): Boolean =
        (daysSinceLast(weights, today) ?: Long.MAX_VALUE) >= REMIND_AFTER_DAYS

    /**
     * A notificação mensal: no 30º dia sem pesar e depois uma vez por semana enquanto continuar sem pesar.
     * Quem nunca se pesou recebe às segundas.
     */
    fun shouldNotify(weights: List<Weighing>, today: LocalDate): Boolean {
        val days = daysSinceLast(weights, today) ?: return today.dayOfWeek == DayOfWeek.MONDAY
        return days >= REMIND_AFTER_DAYS && (days - REMIND_AFTER_DAYS) % 7 == 0L
    }

    fun trend(weights: List<Weighing>): WeightTrend? {
        val last = weights.maxByOrNull { it.date } ?: return null
        val recent = weights.filter { ChronoUnit.DAYS.between(it.date, last.date) <= TREND_WINDOW_DAYS }
        val first = recent.minBy { it.date }
        if (recent.size < 2 || ChronoUnit.DAYS.between(first.date, last.date) < MIN_TREND_SPAN_DAYS) return null
        val xs = recent.map { (it.date.toEpochDay() - first.date.toEpochDay()).toDouble() }
        val ys = recent.map { it.kg }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        return WeightTrend(kgPerWeek = sxy / sxx * 7, from = first.date, to = last.date, count = recent.size)
    }

    /**
     * Peso previsto daqui a [FORECAST_DAYS] dias: parte da última pesagem e segue o ritmo atual.
     * Ancorar na última pesagem (e não na reta) faz a previsão bater com o número que a pessoa acabou de ver.
     */
    fun forecast(weights: List<Weighing>, today: LocalDate): WeightForecast? {
        val last = weights.maxByOrNull { it.date } ?: return null
        if (ChronoUnit.DAYS.between(last.date, today) > MAX_FORECAST_GAP_DAYS) return null
        val trend = trend(weights) ?: return null
        val date = today.plusDays(FORECAST_DAYS.toLong())
        return WeightForecast(date, last.kg + trend.kgPerDay * ChronoUnit.DAYS.between(last.date, date))
    }

    fun progress(goal: WeightGoal, weights: List<Weighing>, today: LocalDate): GoalProgress? {
        val last = weights.maxByOrNull { it.date } ?: return null
        val total = goal.targetKg - goal.startKg
        val done = last.kg - goal.startKg
        val remaining = goal.targetKg - last.kg
        val reached = if (goal.losing) last.kg <= goal.targetKg else last.kg >= goal.targetKg
        val fraction = when {
            reached -> 1.0
            total == 0.0 -> 0.0
            else -> (done / total).coerceIn(0.0, 1.0)
        }
        val trend = trend(weights)
        val eta = if (reached || trend == null || trend.kgPerDay == 0.0 || remaining * trend.kgPerDay <= 0) {
            null
        } else {
            val days = remaining / trend.kgPerDay
            // Folga no ceil: 4 ÷ (2/28) dá 56,000…01 em ponto flutuante e não pode virar 57.
            if (days > 730) null else last.date.plusDays(Math.ceil(days - 1e-9).toLong())
        }
        val needed = goal.by?.takeIf { !reached && it.isAfter(today) }?.let { by ->
            remaining / (ChronoUnit.DAYS.between(today, by) / 7.0)
        }
        return GoalProgress(goal, last.kg, if (reached) 0.0 else abs(remaining), fraction, reached, eta, needed)
    }
}
