import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { decodePlan, encodePlan } from '../src/sync/codec'
import { createTemplate, deleteTemplate, move, nextCode, saveExercise, setDay, updateTemplate } from '../src/sync/edits'
import { canonical, mergePlans, samePlan, TOMBSTONE_TTL_MS } from '../src/sync/merge'
import { emptyPlan, type PlanSnapshot, type SnapshotTemplate } from '../src/sync/types'

const now = 1_000_000
const template = (name: string, updatedAt: number, ...exercises: string[]): SnapshotTemplate => ({
  code: 'A',
  name,
  updatedAt,
  items: exercises.map((exerciseId) => ({ exerciseId, sets: 3, reps: 10, restSec: 90, note: '' })),
})
const plan = (p: Partial<PlanSnapshot>): PlanSnapshot => ({ ...emptyPlan(), ...p })

// Os mesmos casos de app/src/test/.../PlanMergeTest.kt: os dois lados precisam decidir igual.
describe('mergePlans', () => {
  it('fica com a versão mais recente de cada treino e não perde treino novo', () => {
    const merged = mergePlans(
      plan({ templates: { a: template('Peito celular', 300, 'supino_reto'), b: template('Só no celular', 100) } }),
      plan({ templates: { a: template('Peito painel', 200, 'crossover'), c: template('Só no painel', 100) } }),
      now,
    )
    expect(merged.templates.a.name).toBe('Peito celular')
    expect(Object.keys(merged.templates).sort()).toEqual(['a', 'b', 'c'])
  })

  it('empate fica com a nuvem', () => {
    const merged = mergePlans(plan({ templates: { a: template('Celular', 100) } }), plan({ templates: { a: template('Nuvem', 100) } }), now)
    expect(merged.templates.a.name).toBe('Nuvem')
  })

  it('apagar vale contra mudança mais velha e perde para mudança mais nova', () => {
    const merged = mergePlans(
      plan({ templates: { a: template('A', 100), b: template('B', 500) } }),
      plan({ deleted: { 'template:a': 200, 'template:b': 400 } }),
      now,
    )
    expect(merged.templates.a).toBeUndefined()
    expect(merged.deleted['template:a']).toBe(200)
    expect(merged.templates.b).toBeDefined()
    expect(merged.deleted['template:b']).toBeUndefined()
  })

  it('dia fica com a escolha mais recente e treino apagado vira descanso', () => {
    const merged = mergePlans(
      plan({ templates: { a: template('A', 1) }, week: { '1': { templateId: 'a', updatedAt: 50 }, '2': { templateId: 'a', updatedAt: 10 } } }),
      plan({ week: { '1': { templateId: null, updatedAt: 40 }, '2': { templateId: 'x', updatedAt: 20 } }, deleted: { 'template:x': 30 } }),
      now,
    )
    expect(merged.week['1']).toEqual({ templateId: 'a', updatedAt: 50 })
    expect(merged.week['2']).toEqual({ templateId: null, updatedAt: 20 })
  })

  it('os dois lados chegam ao mesmo plano e juntar de novo não muda nada', () => {
    const a = plan({
      templates: { a: template('A1', 10), b: template('B2', 30) },
      week: { '1': { templateId: 'a', updatedAt: 5 } },
      exercises: { custom_1: { name: 'Abdução', equipment: 'Polia', primary: ['GLUTES'], secondary: [], updatedAt: 7 } },
    })
    const b = plan({
      templates: { a: template('A2', 20), b: template('B1', 25) },
      week: { '1': { templateId: 'b', updatedAt: 6 } },
      deleted: { 'exercise:custom_1': 8 },
    })
    const ab = mergePlans(a, b, now)
    expect(canonical(ab)).toBe(canonical(mergePlans(b, a, now)))
    expect(ab.templates.a.name).toBe('A2')
    expect(ab.templates.b.name).toBe('B2')
    expect(ab.exercises).toEqual({})
    expect(samePlan(mergePlans(ab, ab, now), ab)).toBe(true)
  })

  it('apagamento com mais de 90 dias sai da lista', () => {
    const merged = mergePlans(plan({ deleted: { 'template:velho': now - TOMBSTONE_TTL_MS - 1, 'template:novo': now - 5 } }), emptyPlan(), now)
    expect(Object.keys(merged.deleted)).toEqual(['template:novo'])
  })
})

describe('formato do Firestore', () => {
  it('lê o exemplo compartilhado com o app (docs/sync/plano-exemplo.json)', () => {
    const raw = JSON.parse(readFileSync(new URL('../../docs/sync/plano-exemplo.json', import.meta.url), 'utf-8'))
    const decoded = decodePlan(raw)
    expect(decoded.templates.f00d.items[0]).toEqual({ exerciseId: 'supino_reto', sets: 4, reps: 8, restSec: 120, note: 'Descer em 3 s' })
    expect(decoded.week['2']).toEqual({ templateId: null, updatedAt: 0 })
    expect(decoded.exercises.custom_abc.secondary).toEqual(['ADDUCTORS'])
    expect(decoded.deleted).toEqual({ 'template:velho': 42 })
    expect(decodePlan(encodePlan(decoded))).toEqual(decoded)
  })

  it('formato mais novo pede atualização', () => {
    expect(() => decodePlan({ schema: 99 })).toThrow()
  })
})

describe('edições do painel', () => {
  it('carimbam a hora só na parte mudada e respeitam os limites do app', () => {
    const [created, id] = createTemplate(emptyPlan(), 'Pernas', [], 10)
    expect(created.templates[id].code).toBe('A')
    const edited = updateTemplate(created, id, (t) => ({ ...t, items: [{ exerciseId: 'stiff', sets: 99, reps: 0, restSec: 601, note: 'x'.repeat(400) }] }), 20)
    expect(edited.templates[id].items[0]).toMatchObject({ sets: 10, reps: 1, restSec: 600 })
    expect(edited.templates[id].items[0].note).toHaveLength(300)
    expect(edited.templates[id].updatedAt).toBe(20)
    expect(nextCode(edited)).toBe('B')
  })

  it('apagar treino deixa o dia em descanso e registra o apagamento', () => {
    const [created, id] = createTemplate(emptyPlan(), 'Pernas', [], 10)
    const withDay = setDay(created, 3, id, 11)
    const deleted = deleteTemplate(withDay, id, 12)
    expect(deleted.templates[id]).toBeUndefined()
    expect(deleted.week['3']).toEqual({ templateId: null, updatedAt: 12 })
    expect(deleted.deleted[`template:${id}`]).toBe(12)
  })

  it('escolher o mesmo treino no mesmo dia não conta como mudança', () => {
    const [created, id] = createTemplate(emptyPlan(), 'Pernas', [], 10)
    const withDay = setDay(created, 1, id, 11)
    expect(setDay(withDay, 1, id, 99)).toBe(withDay)
  })

  it('exercício próprio tira do secundário o que já é principal', () => {
    const saved = saveExercise(emptyPlan(), 'custom_x', { name: ' Coice ', equipment: 'Polia', primary: ['GLUTES'], secondary: ['GLUTES', 'HAMSTRINGS'] }, 5)
    expect(saved.exercises.custom_x).toEqual({ name: 'Coice', equipment: 'Polia', primary: ['GLUTES'], secondary: ['HAMSTRINGS'], updatedAt: 5 })
  })

  it('move reordena sem mexer na lista original', () => {
    const list = ['a', 'b', 'c', 'd']
    expect(move(list, 3, 1)).toEqual(['a', 'd', 'b', 'c'])
    expect(list).toEqual(['a', 'b', 'c', 'd'])
  })
})
