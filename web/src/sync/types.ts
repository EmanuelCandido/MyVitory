/**
 * O plano de treino, igual ao do app (app/.../domain/sync/PlanSnapshot.kt).
 * Ids estáveis: remoteId dos treinos e id dos exercícios. Cada parte guarda a hora da última mudança (ms).
 */
export interface PlanSnapshot {
  /** remoteId → treino */
  templates: Record<string, SnapshotTemplate>
  /** "1" (segunda) ... "7" (domingo) */
  week: Record<string, SnapshotDay>
  /** Exercícios próprios: id → exercício */
  exercises: Record<string, SnapshotExercise>
  /** "template:<id>" / "exercise:<id>" → quando foi apagado */
  deleted: Record<string, number>
  /** "2026-11-02" → feriado ou treino escolhido para a data */
  dates: Record<string, SnapshotDate>
}

export interface SnapshotTemplate {
  code: string
  name: string
  updatedAt: number
  items: SnapshotItem[]
}

export interface SnapshotItem {
  exerciseId: string
  sets: number
  reps: number
  restSec: number
  note: string
}

export interface SnapshotDay {
  templateId: string | null
  updatedAt: number
  /** A academia não abre neste dia da semana: o recálculo nunca põe treino nele. */
  closed?: boolean
}

/**
 * O que vale numa data. closed: academia fechada (feriado), com label opcional. override: o dia segue templateId
 * (null = descanso) em vez do plano base. Sem os dois, a data segue o plano (a linha fica para o "mais recente vence").
 */
export interface SnapshotDate {
  closed: boolean
  label: string
  override: boolean
  templateId: string | null
  updatedAt: number
}

export interface SnapshotExercise {
  name: string
  equipment: string
  primary: string[]
  secondary: string[]
  updatedAt: number
}

/** 2: academia fechada e datas. O app antigo recusa e pede atualização. */
export const SCHEMA = 2

/** Os mesmos limites do app: fora deles o celular ajusta o valor. */
export const LIMITS = {
  sets: [1, 10],
  reps: [1, 50],
  restSec: [0, 600],
  text: 300,
  label: 60,
} as const

export const emptyPlan = (): PlanSnapshot => ({ templates: {}, week: {}, exercises: {}, deleted: {}, dates: {} })

/** Um dia de treino como o celular envia (app/.../data/sync/HistoryExport.kt). */
export interface DayDoc {
  date: string
  name: string
  status: 'PLANNED' | 'DONE' | 'PARTIAL' | 'MISSED' | 'REST'
  templateId: string | null
  special: boolean
  unavailable: boolean
  /** Academia fechada neste dia ("Academia fechada" ou o nome do feriado). Ausente em apps antigos. */
  closed?: string | null
  movedTo: string | null
  startedAt: number | null
  finishedAt: number | null
  comment: string | null
  exercises: DayExercise[]
}

export interface DayExercise {
  exerciseId: string
  name: string
  note: string
  sets: { kg: number | null; reps: number; done: boolean }[]
}

export interface WeightPoint {
  date: string
  kg: number
}
