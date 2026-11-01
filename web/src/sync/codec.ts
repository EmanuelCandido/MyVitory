import { SCHEMA, type PlanSnapshot, type SnapshotDay, type SnapshotExercise, type SnapshotTemplate } from './types'

type Doc = Record<string, unknown>

const asDoc = (v: unknown): Doc => (v !== null && typeof v === 'object' && !Array.isArray(v) ? (v as Doc) : {})
const str = (v: unknown) => (typeof v === 'string' ? v : '')
const num = (v: unknown) => (typeof v === 'number' && Number.isFinite(v) ? v : 0)
const strs = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : [])

/** Lê o documento plan/current (mesmo formato do SnapshotCodec.kt). */
export function decodePlan(raw: Doc): PlanSnapshot {
  const schema = typeof raw.schema === 'number' ? raw.schema : SCHEMA
  if (schema > SCHEMA) throw new Error(`Plano no formato ${schema}: atualize o painel.`)

  const templates: Record<string, SnapshotTemplate> = {}
  for (const [id, value] of Object.entries(asDoc(raw.templates))) {
    const t = asDoc(value)
    templates[id] = {
      code: str(t.code),
      name: str(t.name),
      updatedAt: num(t.updatedAt),
      items: (Array.isArray(t.items) ? t.items : []).map((item) => {
        const i = asDoc(item)
        return { exerciseId: str(i.exerciseId), sets: num(i.sets), reps: num(i.reps), restSec: num(i.restSec), note: str(i.note) }
      }),
    }
  }

  const week: Record<string, SnapshotDay> = {}
  for (const [day, value] of Object.entries(asDoc(raw.week))) {
    if (!/^[1-7]$/.test(day)) continue
    const d = asDoc(value)
    week[day] = { templateId: typeof d.templateId === 'string' ? d.templateId : null, updatedAt: num(d.updatedAt) }
  }

  const exercises: Record<string, SnapshotExercise> = {}
  for (const [id, value] of Object.entries(asDoc(raw.exercises))) {
    const e = asDoc(value)
    exercises[id] = { name: str(e.name), equipment: str(e.equipment), primary: strs(e.primary), secondary: strs(e.secondary), updatedAt: num(e.updatedAt) }
  }

  const deleted: Record<string, number> = {}
  for (const [key, value] of Object.entries(asDoc(raw.deleted))) deleted[key] = num(value)

  return { templates, week, exercises, deleted }
}

/** Escreve o plano no formato do Firestore. */
export function encodePlan(plan: PlanSnapshot): Doc {
  return {
    schema: SCHEMA,
    templates: plan.templates,
    week: plan.week,
    exercises: plan.exercises,
    deleted: plan.deleted,
  }
}
