import { describe, expect, it } from 'vitest'
import { weekOverview } from '../src/lib/analysis'
import { daysAgo } from '../src/lib/format'
import type { DayDoc } from '../src/sync/types'

const day = (date: string, status: DayDoc['status'], extra: Partial<DayDoc> = {}): DayDoc =>
  ({ date, status, name: 'Treino', templateId: null, special: false, unavailable: false, movedTo: null, exercises: [], ...extra }) as DayDoc

// Sexta, 13/11/2026: a semana vai de segunda 09/11 a domingo 15/11.
const today = new Date(2026, 10, 13)

describe('weekOverview', () => {
  it('conta só a semana atual e acha o último treino', () => {
    const overview = weekOverview(
      [
        day('2026-11-06', 'DONE'),
        day('2026-11-09', 'DONE'),
        day('2026-11-10', 'PARTIAL'),
        day('2026-11-11', 'MISSED'),
        day('2026-11-12', 'REST'),
        day('2026-11-13', 'PLANNED'),
        day('2026-11-14', 'PLANNED'),
      ],
      today,
    )
    expect(overview).toEqual({ done: 2, planned: 5, missed: 1, lastTrained: '2026-11-10' })
  })

  it('treino levado para outro dia não conta como perdido; dia passado sem registro conta', () => {
    const overview = weekOverview(
      [day('2026-11-09', 'MISSED', { movedTo: '2026-11-14' }), day('2026-11-10', 'PLANNED'), day('2026-11-11', 'PLANNED', { unavailable: true })],
      today,
    )
    expect(overview.missed).toBe(1)
    expect(overview.lastTrained).toBeNull()
  })
})

describe('daysAgo', () => {
  it('fala como gente', () => {
    expect(daysAgo('2026-11-13', today)).toBe('hoje')
    expect(daysAgo('2026-11-12', today)).toBe('ontem')
    expect(daysAgo('2026-11-08', today)).toBe('há 5 dias')
    expect(daysAgo('2026-10-20', today)).toBe('20 out')
  })
})
