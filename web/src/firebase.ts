import { initializeApp } from 'firebase/app'
import { connectAuthEmulator, getAuth, GoogleAuthProvider } from 'firebase/auth'
import { connectFirestoreEmulator, getFirestore } from 'firebase/firestore'

const env = import.meta.env

/** Sem .env.local o painel mostra como configurar, em vez de quebrar. */
export const configured = Boolean(env.VITE_FIREBASE_PROJECT_ID && env.VITE_FIREBASE_API_KEY)

const app = initializeApp({
  apiKey: env.VITE_FIREBASE_API_KEY || 'sem-chave',
  authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: env.VITE_FIREBASE_PROJECT_ID || 'demo-myvitory',
  appId: env.VITE_FIREBASE_APP_ID,
})

export const auth = getAuth(app)
export const db = getFirestore(app)
export const google = new GoogleAuthProvider()

export const usingEmulators = env.VITE_USE_EMULATORS === 'true'
if (usingEmulators) {
  // Portas do firebase.json; dá para trocar com VITE_EMULATOR_AUTH / VITE_EMULATOR_FIRESTORE (host:porta).
  const [firestoreHost, firestorePort] = (env.VITE_EMULATOR_FIRESTORE || '127.0.0.1:8080').split(':')
  connectAuthEmulator(auth, `http://${env.VITE_EMULATOR_AUTH || '127.0.0.1:9099'}`, { disableWarnings: true })
  connectFirestoreEmulator(db, firestoreHost, Number(firestorePort))
}
