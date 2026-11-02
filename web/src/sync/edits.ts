import { LIMITS, type PlanSnapshot, type SnapshotDate, type SnapshotExercise, type SnapshotItem, type SnapshotTemplate } from './types'

/**
 * Mudanças que o painel faz no plano. Cada uma devolve um plano novo (sem mexer no antigo) e carimba a hora
 * na parte mudada: é essa hora que decide qual versão vale quando o celular também mudou.
 */

const clamp = (value: number, [min, max]: readonly [number, number]) => Math.min(max, Math.max(min, Math.round(value)))

export const normalizeItem = (item: SnapshotItem): SnapshotItem => ({
  ...item,
  sets: clamp(item.sets, LIMITS.sets),
  reps: clamp(item.reps, LIMITS.reps),
  restSec: clamp(item.restSec, LIMITS.restSec),
  note: item.note.slice(0, LIMITS.text),
})

export function newId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/** Próximo código livre (A, B, C...), como o app faz. */
export function nextCode(plan: PlanSnapshot): string {
  const used = new Set(Object.values(plan.templates).map((t) => t.code))
  for (let c = 65; c <= 90; c++) if (!used.has(String.fromCharCode(c))) return String.fromCharCode(c)
  return String(used.size + 1)
}

export function updateTemplate(plan: PlanSnapshot, id: string, change: (t: SnapshotTemplate) => SnapshotTemplate, now = Date.now()): PlanSnapshot {
  const current = plan.templates[id]
  if (!current) return plan
  const next = change(current)
  return { ...plan, templates: { ...plan.templates, [id]: { ...next, items: next.items.map(normalizeItem), updatedAt: now } } }
}

export function createTemplate(plan: PlanSnapshot, name: string, items: SnapshotItem[] = [], now = Date.now()): [PlanSnapshot, string] {
  const id = newId()
  const code = nextCode(plan)
  const template: SnapshotTemplate = { code, name: name.trim() || `Treino ${code}`, updatedAt: now, items: items.map(normalizeItem) }
  return [{ ...plan, templates: { ...plan.templates, [id]: template } }, id]
}

export function deleteTemplate(plan: PlanSnapshot, id: string, now = Date.now()): PlanSnapshot {
  const templates = { ...plan.templates }
  delete templates[id]
  const week = Object.fromEntries(
    Object.entries(plan.week).map(([day, d]) => [day, d.templateId === id ? { templateId: null, updatedAt: now } : d]),
  )
  return { ...plan, templates, week, deleted: { ...plan.deleted, [`template:${id}`]: now } }
}

export function setDay(plan: PlanSnapshot, day: number, templateId: string | null, now = Date.now()): PlanSnapshot {
  const current = plan.week[String(day)]
  if ((current?.templateId ?? null) === templateId) return plan
  return { ...plan, week: { ...plan.week, [String(day)]: { ...current, templateId, updatedAt: now } } }
}

/** Academia fechada (ou aberta de novo) num dia da semana. Fechar põe descanso no plano base. */
export function setClosedWeekday(plan: PlanSnapshot, day: number, closed: boolean, now = Date.now()): PlanSnapshot {
  const current = plan.week[String(day)]
  if ((current?.closed ?? false) === closed) return plan
  const templateId = closed ? null : (current?.templateId ?? null)
  return { ...plan, week: { ...plan.week, [String(day)]: { templateId, updatedAt: now, closed } } }
}

/** O que a data passa a ter: academia fechada, um treino (ou descanso) escolhido, ou o plano base. */
export type DateChoice = { kind: 'plan' } | { kind: 'closed'; label: string } | { kind: 'template'; templateId: string | null }

export function setDate(plan: PlanSnapshot, date: string, choice: DateChoice, now = Date.now()): PlanSnapshot {
  const entry: SnapshotDate =
    choice.kind === 'closed'
      ? { closed: true, label: choice.label.trim().slice(0, LIMITS.label), override: false, templateId: null, updatedAt: now }
      : choice.kind === 'template'
        ? { closed: false, label: '', override: true, templateId: choice.templateId, updatedAt: now }
        : { closed: false, label: '', override: false, templateId: null, updatedAt: now }
  const current = plan.dates[date]
  const same = current
    ? current.closed === entry.closed && current.label === entry.label && current.override === entry.override && current.templateId === entry.templateId
    : choice.kind === 'plan'
  if (same) return plan
  return { ...plan, dates: { ...plan.dates, [date]: entry } }
}

/** Como a data está no plano: o que foi escolhido para ela ou, sem escolha, o plano base. */
export function dateChoice(plan: PlanSnapshot, date: string): DateChoice {
  const d = plan.dates[date]
  if (d?.closed) return { kind: 'closed', label: d.label }
  if (d?.override) return { kind: 'template', templateId: d.templateId }
  return { kind: 'plan' }
}

export function saveExercise(plan: PlanSnapshot, id: string, exercise: Omit<SnapshotExercise, 'updatedAt'>, now = Date.now()): PlanSnapshot {
  const primary = [...new Set(exercise.primary)]
  const secondary = [...new Set(exercise.secondary)].filter((m) => !primary.includes(m))
  const deleted = { ...plan.deleted }
  delete deleted[`exercise:${id}`]
  return {
    ...plan,
    exercises: { ...plan.exercises, [id]: { ...exercise, name: exercise.name.trim(), primary, secondary, updatedAt: now } },
    deleted,
  }
}

export function deleteExercise(plan: PlanSnapshot, id: string, now = Date.now()): PlanSnapshot {
  const exercises = { ...plan.exercises }
  delete exercises[id]
  return { ...plan, exercises, deleted: { ...plan.deleted, [`exercise:${id}`]: now } }
}

/** Treinos que usam o exercício: exercício em uso não pode ser apagado. */
export const templatesUsing = (plan: PlanSnapshot, exerciseId: string) =>
  Object.values(plan.templates).filter((t) => t.items.some((i) => i.exerciseId === exerciseId))

export const newItem = (exerciseId: string): SnapshotItem => ({ exerciseId, sets: 3, reps: 10, restSec: 90, note: '' })

export function move<T>(list: T[], from: number, to: number): T[] {
  const next = [...list]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}
