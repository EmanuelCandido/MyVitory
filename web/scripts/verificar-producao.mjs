// Confere o projeto Firebase de verdade (o do .env.local) sem deixar rastro: uma atleta temporária entra
// anonimamente, grava ficha, plano e código como o app faz, um estranho tenta ler (e não pode) e, no fim,
// tudo é apagado, inclusive as duas contas anônimas.
//
//   node scripts/verificar-producao.mjs
import { readFileSync } from 'node:fs'
import { deleteApp, initializeApp } from 'firebase/app'
import { deleteUser, getAuth, signInAnonymously } from 'firebase/auth'
import { deleteDoc, doc, getDoc, getFirestore, serverTimestamp, setDoc, Timestamp } from 'firebase/firestore'

const env = Object.fromEntries(
  readFileSync(new URL('../.env.local', import.meta.url), 'utf-8')
    .split(/\r?\n/)
    .filter((l) => l.includes('=') && !l.startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]),
)
const config = { apiKey: env.VITE_FIREBASE_API_KEY, authDomain: env.VITE_FIREBASE_AUTH_DOMAIN, projectId: env.VITE_FIREBASE_PROJECT_ID, appId: env.VITE_FIREBASE_APP_ID }

const ok = (msg) => console.log(`✔ ${msg}`)
const athleteApp = initializeApp(config, 'atleta')
const strangerApp = initializeApp(config, 'estranho')
const athleteAuth = getAuth(athleteApp)
const db = getFirestore(athleteApp)
const strangerAuth = getAuth(strangerApp)
const strangerDb = getFirestore(strangerApp)

const { user } = await signInAnonymously(athleteAuth)
ok('login anônimo (atleta)')
const id = user.uid
const code = `T${Math.random().toString(36).slice(2, 9).toUpperCase()}`.slice(0, 8).padEnd(8, 'X')
try {
  await setDoc(doc(db, 'athletes', id), { ownerUid: id, name: 'Teste automático', createdAt: serverTimestamp() })
  await setDoc(doc(db, 'athletes', id, 'plan', 'current'), { schema: 1, templates: {}, week: {}, exercises: {}, deleted: {}, updatedBy: 'app' })
  await setDoc(doc(db, 'invites', code), { athleteId: id, name: 'Teste', expiresAt: Timestamp.fromMillis(Date.now() + 5 * 60_000) })
  ok('ficha, plano e código gravados pelas regras')
  if (!(await getDoc(doc(db, 'athletes', id, 'plan', 'current'))).exists()) throw new Error('plano não voltou na leitura')
  ok('plano lido de volta')

  await signInAnonymously(strangerAuth)
  const denied = await getDoc(doc(strangerDb, 'athletes', id, 'plan', 'current')).then(() => false, () => true)
  if (!denied) throw new Error('um estranho conseguiu ler o plano!')
  ok('estranho não lê o plano')
} finally {
  await deleteDoc(doc(db, 'invites', code)).catch(() => undefined)
  await deleteDoc(doc(db, 'athletes', id, 'plan', 'current')).catch(() => undefined)
  await deleteDoc(doc(db, 'athletes', id)).catch(() => undefined)
  await deleteUser(user).catch(() => undefined)
  if (strangerAuth.currentUser) await deleteUser(strangerAuth.currentUser).catch(() => undefined)
  ok('dados e contas de teste apagados')
  await Promise.all([deleteApp(athleteApp), deleteApp(strangerApp)])
}
process.exit(0)
