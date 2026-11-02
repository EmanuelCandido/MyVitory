import { readFileSync } from 'node:fs'
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from '@firebase/rules-unit-testing'
import { deleteDoc, deleteField, doc, getDoc, getDocs, collection, serverTimestamp, setDoc, Timestamp, updateDoc } from 'firebase/firestore'

/*
 * As regras de firebase/firestore.rules contra o emulador (npm run test:regras).
 * Atleta = conta anônima do app (uid = id da ficha). Treinador = conta Google no painel.
 */
let env: RulesTestEnvironment

const ATHLETE = 'atleta1'
const COACH = 'treinador1'
const STRANGER = 'estranho'
const CODE = 'ABCD2345'

const plan = { schema: 2, templates: {}, week: {}, exercises: {}, deleted: {}, dates: {}, updatedBy: 'web' }
const inMinutes = (m: number) => Timestamp.fromMillis(Date.now() + m * 60_000)

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-myvitory',
    firestore: { rules: readFileSync(new URL('../../firebase/firestore.rules', import.meta.url), 'utf-8'), host: '127.0.0.1', port: 8080 },
  })
})

afterAll(async () => env?.cleanup())

beforeEach(async () => {
  await env.clearFirestore()
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore()
    await setDoc(doc(db, 'athletes', ATHLETE), { ownerUid: ATHLETE, name: 'Vitória' })
    await setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current'), { ...plan, updatedBy: 'app' })
    await setDoc(doc(db, 'athletes', ATHLETE, 'days', '2026-10-30'), { date: '2026-10-30', status: 'DONE' })
    await setDoc(doc(db, 'invites', CODE), { athleteId: ATHLETE, name: 'Vitória', expiresAt: inMinutes(30) })
  })
})

const as = (uid: string) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore()

const pair = (uid: string, code = CODE, extra: Record<string, unknown> = {}) =>
  updateDoc(doc(as(uid), 'athletes', ATHLETE), {
    [`coaches.${uid}`]: { name: 'Emanuel', email: 'e@x.com', addedAt: serverTimestamp() },
    pairCode: code,
    ...extra,
  })

describe('atleta', () => {
  it('cria a própria ficha sem treinadores, mas não a de outra pessoa', async () => {
    await assertSucceeds(setDoc(doc(as('nova'), 'athletes', 'nova'), { ownerUid: 'nova', name: 'Ana' }))
    await assertFails(setDoc(doc(as('nova'), 'athletes', 'outra'), { ownerUid: 'outra', name: 'Ana' }))
    await assertFails(setDoc(doc(as('nova2'), 'athletes', 'nova2'), { ownerUid: 'nova2', name: 'Ana', coaches: { [STRANGER]: { name: 'x' } } }))
  })

  it('escreve plano, dias e peso; gera código só para si', async () => {
    const db = as(ATHLETE)
    await assertSucceeds(setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current'), { ...plan, updatedBy: 'app' }))
    await assertSucceeds(setDoc(doc(db, 'athletes', ATHLETE, 'days', '2026-10-31'), { date: '2026-10-31' }))
    await assertSucceeds(setDoc(doc(db, 'athletes', ATHLETE, 'private', 'weights'), { points: [] }))
    await assertSucceeds(setDoc(doc(db, 'invites', 'ZZZZ2345'), { athleteId: ATHLETE, expiresAt: inMinutes(30) }))
    await assertFails(setDoc(doc(db, 'invites', 'YYYY2345'), { athleteId: 'outra', expiresAt: inMinutes(30) }))
    await assertFails(setDoc(doc(db, 'invites', 'XXXX2345'), { athleteId: ATHLETE, expiresAt: inMinutes(60 * 24) }))
  })
})

describe('pareamento', () => {
  it('treinador com código válido se põe como treinador', async () => {
    await assertSucceeds(pair(COACH))
  })

  it('código errado, vencido ou de outra atleta não vale', async () => {
    await assertFails(pair(COACH, 'NAOEXISTE'))
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'invites', 'VENCIDO2'), { athleteId: ATHLETE, expiresAt: inMinutes(-1) })
      await setDoc(doc(ctx.firestore(), 'invites', 'OUTRA234'), { athleteId: 'outra', expiresAt: inMinutes(30) })
    })
    await assertFails(pair(COACH, 'VENCIDO2'))
    await assertFails(pair(COACH, 'OUTRA234'))
  })

  it('não dá para pôr outra pessoa nem mudar outros campos', async () => {
    await assertFails(
      updateDoc(doc(as(COACH), 'athletes', ATHLETE), { [`coaches.${STRANGER}`]: { name: 'x', email: '', addedAt: serverTimestamp() }, pairCode: CODE }),
    )
    await assertFails(pair(COACH, CODE, { name: 'Outro nome' }))
  })

  it('não lista códigos', async () => {
    await assertFails(getDocs(collection(as(STRANGER), 'invites')))
    await assertSucceeds(getDoc(doc(as(STRANGER), 'invites', CODE)))
  })
})

describe('treinador pareado', () => {
  beforeEach(async () => {
    await pair(COACH)
  })

  it('lê a ficha, o plano, os dias e o peso; edita o plano', async () => {
    const db = as(COACH)
    await assertSucceeds(getDoc(doc(db, 'athletes', ATHLETE)))
    await assertSucceeds(getDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current')))
    await assertSucceeds(getDocs(collection(db, 'athletes', ATHLETE, 'days')))
    await assertSucceeds(getDoc(doc(db, 'athletes', ATHLETE, 'private', 'weights')))
    await assertSucceeds(setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current'), plan))
  })

  it('não escreve histórico nem peso, não apaga o plano e não grava plano fora do formato', async () => {
    const db = as(COACH)
    await assertFails(setDoc(doc(db, 'athletes', ATHLETE, 'days', '2026-10-31'), { date: '2026-10-31' }))
    await assertFails(setDoc(doc(db, 'athletes', ATHLETE, 'private', 'weights'), { points: [] }))
    await assertFails(deleteDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current')))
    await assertFails(setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current'), { ...plan, schema: 3 }))
    // App antigo (formato 1) não apaga as datas do plano.
    await assertFails(setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current'), { ...plan, schema: 1 }))
    await assertFails(setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'outro'), plan))
  })

  it('pode sair sozinho; depois perde o acesso', async () => {
    await assertSucceeds(updateDoc(doc(as(COACH), 'athletes', ATHLETE), { [`coaches.${COACH}`]: deleteField() }))
    await assertFails(getDoc(doc(as(COACH), 'athletes', ATHLETE, 'plan', 'current')))
  })

  it('a atleta remove o treinador', async () => {
    await assertSucceeds(updateDoc(doc(as(ATHLETE), 'athletes', ATHLETE), { [`coaches.${COACH}`]: deleteField() }))
    await assertFails(getDoc(doc(as(COACH), 'athletes', ATHLETE)))
  })
})

describe('estranho', () => {
  it('não vê nem mexe em nada da atleta', async () => {
    const db = as(STRANGER)
    await assertFails(getDoc(doc(db, 'athletes', ATHLETE)))
    await assertFails(getDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current')))
    await assertFails(getDocs(collection(db, 'athletes', ATHLETE, 'days')))
    await assertFails(setDoc(doc(db, 'athletes', ATHLETE, 'plan', 'current'), plan))
    await assertFails(getDocs(collection(db, 'athletes')))
  })

  it('sem login, nada', async () => {
    const db = env.unauthenticatedContext().firestore()
    await assertFails(getDoc(doc(db, 'invites', CODE)))
    await assertFails(getDoc(doc(db, 'athletes', ATHLETE)))
  })
})
