import { muscleLabel, muscles, exerciseById } from './catalog'
import type { DayDoc, PlanSnapshot, SnapshotTemplate } from '../sync/types'
import { addDays, parseDay, weekStart } from './format'

/** Segundos por repetição, para estimar a duração de um treino. */
const SECONDS_PER_REP = 3

/** Duração estimada: execução das séries + descanso entre elas. */
export function estimateMinutes(template: SnapshotTemplate): number {
  const seconds = template.items.reduce((total, i) => total + i.sets * i.reps * SECONDS_PER_REP + Math.max(0, i.sets - 1) * i.restSec, 0)
  return Math.round(seconds / 60)
}

export const totalSets = (template: SnapshotTemplate) => template.items.reduce((n, i) => n + i.sets, 0)

/** Músculos principais do treino, na ordem em que aparecem. */
export function primaryMuscles(plan: PlanSnapshot, template: SnapshotTemplate): string[] {
  const seen = new Set<string>()
  template.items.forEach((i) => exerciseById(plan, i.exerciseId).primary.forEach((m) => seen.add(m)))
  return [...seen]
}

const recovery = new Map(muscles.map((m) => [m.id, m.recoveryHours]))

export interface RecoveryWarning {
  day: number
  muscle: string
  previousDay: number
  gapHours: number
  neededHours: number
}

/**
 * Músculo principal que volta antes da janela de recuperação (mesma tabela do app), olhando a semana como um
 * ciclo: domingo é seguido pela segunda. Aviso, não regra: o recálculo do app trata como custo, não proibição.
 */
export function recoveryWarnings(plan: PlanSnapshot): RecoveryWarning[] {
  const byDay = new Map<number, string[]>()
  for (let day = 1; day <= 7; day++) {
    const id = plan.week[String(day)]?.templateId
    const template = id ? plan.templates[id] : undefined
    if (template) byDay.set(day, primaryMuscles(plan, template))
  }
  const warnings: RecoveryWarning[] = []
  for (const [day, list] of byDay) {
    for (const muscle of list) {
      const needed = recovery.get(muscle) ?? 48
      for (let back = 1; back * 24 < needed && back < 7; back++) {
        const previousDay = ((day - 1 - back + 7) % 7) + 1
        if (byDay.get(previousDay)?.includes(muscle)) {
          warnings.push({ day, muscle, previousDay, gapHours: back * 24, neededHours: needed })
          break
        }
      }
    }
  }
  return warnings
}

export const warningText = (w: RecoveryWarning) =>
  `${muscleLabel(w.muscle)}: ${w.gapHours} h desde o treino anterior (pede ${w.neededHours} h)`

export interface HistoryStats {
  doneLast28: number
  plannedLast28: number
  missedLast28: number
  streakWeeks: number
}

const trained = (d: DayDoc) => d.status === 'DONE' || d.status === 'PARTIAL'

export function historyStats(days: DayDoc[], today = new Date()): HistoryStats {
  const from = addDays(today, -28)
  const recent = days.filter((d) => {
    const date = parseDay(d.date)
    return date >= from && date <= today
  })
  // Semanas seguidas (a partir da anterior) com pelo menos um treino feito.
  let streakWeeks = 0
  let start = addDays(weekStart(today), -7)
  for (;;) {
    const end = addDays(start, 7)
    const any = days.some((d) => {
      const date = parseDay(d.date)
      return date >= start && date < end && trained(d)
    })
    if (!any) break
    streakWeeks++
    start = addDays(start, -7)
  }
  return {
    doneLast28: recent.filter(trained).length,
    plannedLast28: recent.filter((d) => d.status !== 'REST').length,
    missedLast28: recent.filter((d) => d.status === 'MISSED').length,
    streakWeeks,
  }
}

export interface LoadPoint {
  date: string
  topKg: number
  reps: string
}

/** Maior carga feita em cada treino com o exercício (para o gráfico de evolução). */
export function loadHistory(days: DayDoc[], exerciseId: string): LoadPoint[] {
  return days
    .filter(trained)
    .flatMap((d) => {
      const done = d.exercises.filter((e) => e.exerciseId === exerciseId).flatMap((e) => e.sets.filter((s) => s.done))
      if (done.length === 0) return []
      return [{ date: d.date, topKg: Math.max(...done.map((s) => s.kg ?? 0)), reps: done.map((s) => s.reps).join(', ') }]
    })
    .sort((a, b) => a.date.localeCompare(b.date))
}

/** Exercícios que aparecem no histórico feito, do mais frequente para o menos. */
export function trainedExercises(days: DayDoc[]): { id: string; name: string; count: number }[] {
  const counts = new Map<string, { id: string; name: string; count: number }>()
  days.filter(trained).forEach((d) =>
    d.exercises
      .filter((e) => e.sets.some((s) => s.done))
      .forEach((e) => {
        const current = counts.get(e.exerciseId) ?? { id: e.exerciseId, name: e.name, count: 0 }
        current.count++
        counts.set(e.exerciseId, current)
      }),
  )
  return [...counts.values()].sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, 'pt-BR'))
}
