import type { PlanSnapshot, SnapshotDay } from './types'

/** Apagamentos mais velhos que isso já chegaram aos dois lados e saem da lista. */
export const TOMBSTONE_TTL_MS = 90 * 24 * 60 * 60 * 1000

/**
 * Junta duas versões do plano. Mesma regra do app (PlanMerge.kt), para os dois chegarem ao mesmo resultado:
 * cada treino, exercício próprio e dia fica com a versão de updatedAt mais recente; empate fica com [remote];
 * um apagamento vale se for mais novo que a última mudança daquela parte.
 */
export function mergePlans(local: PlanSnapshot, remote: PlanSnapshot, now: number): PlanSnapshot {
  const deleted: Record<string, number> = {}
  for (const key of new Set([...Object.keys(local.deleted), ...Object.keys(remote.deleted)])) {
    const values = [local.deleted[key], remote.deleted[key]].filter((v): v is number => v !== undefined)
    deleted[key] = Math.max(...values)
  }

  const templates = pick(local.templates, remote.templates, 'template:', deleted)
  const exercises = pick(local.exercises, remote.exercises, 'exercise:', deleted)

  const week: Record<string, SnapshotDay> = {}
  for (const day of [...new Set([...Object.keys(local.week), ...Object.keys(remote.week)])].sort()) {
    const chosen = newer(local.week[day], remote.week[day])!
    // Dia que aponta para um treino apagado vira descanso, sem mudar a hora (o app faz o mesmo).
    week[day] = chosen.templateId !== null && !(chosen.templateId in templates) ? { ...chosen, templateId: null } : chosen
  }

  const kept: Record<string, number> = {}
  for (const key of Object.keys(deleted).sort()) if (now - deleted[key] < TOMBSTONE_TTL_MS) kept[key] = deleted[key]

  return { templates, week, exercises, deleted: kept }
}

function pick<T extends { updatedAt: number }>(
  local: Record<string, T>,
  remote: Record<string, T>,
  prefix: string,
  deleted: Record<string, number>,
): Record<string, T> {
  const result: Record<string, T> = {}
  for (const id of [...new Set([...Object.keys(local), ...Object.keys(remote)])].sort()) {
    const chosen = newer(local[id], remote[id])!
    const deletedAt = deleted[prefix + id]
    if (deletedAt === undefined) {
      result[id] = chosen
    } else if (chosen.updatedAt > deletedAt) {
      // Mudou depois de apagado: volta, e o apagamento deixa de valer.
      result[id] = chosen
      delete deleted[prefix + id]
    }
  }
  return result
}

function newer<T extends { updatedAt: number }>(local: T | undefined, remote: T | undefined): T | undefined {
  if (local === undefined) return remote
  if (remote === undefined) return local
  return local.updatedAt > remote.updatedAt ? local : remote
}

/** Texto estável do plano (chaves em ordem), para comparar duas versões. */
export function canonical(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`
  if (value !== null && typeof value === 'object') {
    const entries = Object.keys(value as Record<string, unknown>)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${canonical((value as Record<string, unknown>)[k])}`)
    return `{${entries.join(',')}}`
  }
  return JSON.stringify(value)
}

export const samePlan = (a: PlanSnapshot | null, b: PlanSnapshot | null) => a !== null && b !== null && canonical(a) === canonical(b)
