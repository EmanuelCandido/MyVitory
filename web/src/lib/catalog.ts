import data from '../data/catalog.json'
import type { PlanSnapshot } from '../sync/types'

/** Exercício como o painel mostra: do catálogo do app (gerado) ou criado pela atleta/treinador. */
export interface Exercise {
  id: string
  name: string
  equipment: string
  primary: string[]
  secondary: string[]
  gif: string | null
  videoId: string | null
  tips: string[]
  custom: boolean
}

export interface MuscleInfo {
  id: string
  label: string
  recoveryHours: number
}

export const muscles: MuscleInfo[] = data.muscles
const muscleLabels = new Map(muscles.map((m) => [m.id, m.label]))
export const muscleLabel = (id: string) => muscleLabels.get(id) ?? id

export const catalog: Exercise[] = data.exercises.map((e) => ({ ...e, custom: false }))
const catalogById = new Map(catalog.map((e) => [e.id, e]))

/** Divisão de exemplo do app (ABCDE), oferecida como modelo pronto. */
export const models = data.templates

export const equipments =[...new Set(catalog.map((e) => e.equipment))].sort((a, b) => a.localeCompare(b, 'pt-BR'))

/** Catálogo + exercícios próprios do plano, em ordem alfabética. */
export function allExercises(plan: PlanSnapshot): Exercise[] {
  const custom = Object.entries(plan.exercises).map(([id, e]) => ({
    id,
    name: e.name,
    equipment: e.equipment,
    primary: e.primary,
    secondary: e.secondary,
    gif: null,
    videoId: null,
    tips: [],
    custom: true,
  }))
  return [...catalog, ...custom].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
}

export function exerciseById(plan: PlanSnapshot, id: string): Exercise {
  const fromCatalog = catalogById.get(id)
  if (fromCatalog) return fromCatalog
  const custom = plan.exercises[id]
  if (custom) return { id, ...custom, gif: null, videoId: null, tips: [], custom: true }
  return { id, name: id, equipment: '', primary: [], secondary: [], gif: null, videoId: null, tips: [], custom: true }
}

/**
 * Mesmo músculo principal, do mais parecido para o menos. Mesma pontuação das alternativas do app
 * (domain/Alternatives.kt): equipamento diferente ganha um pouco, porque o motivo comum da troca é aparelho ocupado.
 */
export function similarTo(target: Exercise, pool: Exercise[]): Exercise[] {
  const samePrimary = (e: Exercise) =>
    e.primary.length === target.primary.length && e.primary.every((m) => target.primary.includes(m))
  const score = (e: Exercise) =>
    (samePrimary(e) ? 3 : 0) +
    e.primary.filter((m) => target.primary.includes(m)).length * 2 +
    e.secondary.filter((m) => target.secondary.includes(m)).length -
    e.secondary.filter((m) => !target.secondary.includes(m) && !target.primary.includes(m)).length * 0.5 +
    (e.equipment !== target.equipment ? 0.5 : 0)
  return pool
    .filter((e) => e.id !== target.id && e.primary.some((m) => target.primary.includes(m)))
    .sort((a, b) => score(b) - score(a) || a.id.localeCompare(b.id))
}

export const normalize = (text: string) =>
  text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
