// Faz o papel do celular da atleta contra os emuladores (npm run emuladores), seguindo as mesmas regras:
// conta anônima, ficha, plano (a divisão de exemplo do app), quatro semanas de histórico, peso e um código.
//
//   node scripts/simular-celular.mjs
//
// Depois, no painel em modo emulador (npm run dev:emulador), entre e digite o código impresso.
import { readFileSync } from 'node:fs'
import { initializeApp } from 'firebase/app'
import { connectAuthEmulator, getAuth, signInAnonymously } from 'firebase/auth'
import { connectFirestoreEmulator, doc, getFirestore, serverTimestamp, setDoc, Timestamp, writeBatch } from 'firebase/firestore'

const catalog = JSON.parse(readFileSync(new URL('../src/data/catalog.json', import.meta.url), 'utf-8'))
const app = initializeApp({ apiKey: 'demo-key', projectId: 'demo-myvitory', appId: 'demo-app' })
const auth = getAuth(app)
const db = getFirestore(app)
// Mesmas variáveis que as ferramentas do Firebase usam; sem elas, as portas do firebase.json.
const [firestoreHost, firestorePort] = (process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8080').split(':')
connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'}`, { disableWarnings: true })
connectFirestoreEmulator(db, firestoreHost, Number(firestorePort))

const { user } = await signInAnonymously(auth)
const id = user.uid
const athlete = doc(db, 'athletes', id)
await setDoc(athlete, { ownerUid: id, name: 'Vitória', createdAt: serverTimestamp(), shareWeight: true }, { merge: true })

// Plano: a divisão ABCDE do app, segunda a sexta.
const names = new Map(catalog.exercises.map((e) => [e.id, e.name]))
const templates = {}
const week = {}
catalog.templates.forEach((t, i) => {
  const remoteId = `seed${t.code.toLowerCase()}${'0'.repeat(27)}`
  templates[remoteId] = { code: t.code, name: t.name, updatedAt: 0, items: t.items.map((it) => ({ ...it, note: '' })) }
  week[String(t.day)] = { templateId: remoteId, updatedAt: 0 }
  if (i === 0) templates[remoteId].items[0].note = 'Escápulas juntas, descer controlando'
})
for (let d = 6; d <= 7; d++) week[String(d)] = { templateId: null, updatedAt: 0 }
await setDoc(doc(db, 'athletes', id, 'plan', 'current'), { schema: 2, templates, week, exercises: {}, deleted: {}, dates: {}, updatedBy: 'app', savedAt: serverTimestamp() })

// Histórico: quatro semanas, cargas subindo, uma falta, um parcial, comentários.
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const today = new Date()
const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - ((today.getDay() + 6) % 7))
const batch = writeBatch(db)
const byDay = Object.fromEntries(catalog.templates.map((t) => [t.day, t]))
for (let w = -4; w <= 0; w++) {
  for (let day = 1; day <= 5; day++) {
    const date = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + w * 7 + day - 1)
    const t = byDay[day]
    const past = date < new Date(today.getFullYear(), today.getMonth(), today.getDate())
    let status = past ? 'DONE' : 'PLANNED'
    if (w === -2 && day === 3) status = 'MISSED'
    if (w === -1 && day === 4) status = 'PARTIAL'
    const progress = w + 4
    const exercises = t.items.map((it, index) => ({
      exerciseId: it.exerciseId,
      name: names.get(it.exerciseId) ?? it.exerciseId,
      note: '',
      sets: Array.from({ length: it.sets }, (_, s) => ({
        kg: status === 'PLANNED' ? null : 10 + index * 5 + progress * 2.5,
        reps: it.reps,
        done: status === 'DONE' || (status === 'PARTIAL' && s < 2),
      })),
    }))
    const started = date.getTime() + 18 * 3600_000
    batch.set(doc(db, 'athletes', id, 'days', iso(date)), {
      date: iso(date),
      name: t.name,
      status,
      templateId: `seed${t.code.toLowerCase()}${'0'.repeat(27)}`,
      special: false,
      unavailable: false,
      movedTo: null,
      startedAt: status === 'DONE' || status === 'PARTIAL' ? started : null,
      finishedAt: status === 'DONE' || status === 'PARTIAL' ? started + (48 + day * 3) * 60_000 : null,
      comment: w === -1 && day === 3 ? 'Senti o joelho no agachamento, diminuí a carga.' : w === -3 && day === 5 ? 'Treino ótimo!' : null,
      exercises,
    })
  }
}
const weights = Array.from({ length: 6 }, (_, i) => ({ date: iso(new Date(today.getFullYear(), today.getMonth(), today.getDate() - (5 - i) * 15)), kg: 63.4 - i * 0.4 }))
batch.set(doc(db, 'athletes', id, 'private', 'weights'), { points: weights, savedAt: serverTimestamp() })
await batch.commit()

// Código de pareamento, como o app gera.
const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'
const code = Array.from({ length: 8 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('')
await setDoc(doc(db, 'invites', code), { athleteId: id, name: 'Vitória', expiresAt: Timestamp.fromMillis(Date.now() + 30 * 60_000), createdAt: serverTimestamp() })
await setDoc(athlete, { lastSyncAt: serverTimestamp(), appVersion: '0.10.0' }, { merge: true })

console.log(`Atleta ${id} criada. Código de pareamento: ${code.slice(0, 4)}-${code.slice(4)}`)
process.exit(0)
