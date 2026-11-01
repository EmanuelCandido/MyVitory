import { useEffect, useRef, useState } from 'react'
import { signOut, type User } from 'firebase/auth'
import { auth, configured, usingEmulators } from './firebase'
import { useAthletes, useUser } from './lib/data'
import { AthletePage } from './pages/AthletePage'
import { HomePage, LoginPage, SetupPage } from './pages/EntryPages'

/** A atleta aberta fica no endereço (#/atleta/<id>): recarregar a página volta para ela. */
function useRoute(): [string | null, (id: string | null) => void] {
  const read = () => decodeURIComponent(location.hash.match(/^#\/atleta\/(.+)$/)?.[1] ?? '') || null
  const [id, setId] = useState(read)
  useEffect(() => {
    const onHash = () => setId(read())
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])
  return [id, (next) => (location.hash = next ? `#/atleta/${encodeURIComponent(next)}` : '#/')]
}

export function App() {
  const user = useUser()
  if (!configured && !usingEmulators) return <SetupPage />
  if (user === undefined) return <p className="empty">Carregando…</p>
  if (user === null) return <LoginPage />
  return <Signed user={user} />
}

function Signed({ user }: { user: User }) {
  const athletes = useAthletes(user.uid)
  const [athleteId, open] = useRoute()
  const athlete = athletes?.find((a) => a.id === athleteId)

  // Só uma atleta: abre direto nela na primeira carga (voltar ao início continua possível).
  const autoOpened = useRef(false)
  useEffect(() => {
    if (autoOpened.current || athletes === undefined) return
    autoOpened.current = true
    if (!athleteId && athletes.length === 1 && !athletes[0].lost) open(athletes[0].id)
  }, [athleteId, athletes, open])

  return (
    <>
      <header className="topbar">
        <a className="brand" href="#/" style={{ color: 'inherit', textDecoration: 'none' }}>
          <span className="brand-mark" /> MyVitory <small>painel do treinador</small>
        </a>
        {athletes && athletes.length > 1 && (
          <select className="select" style={{ width: 'auto' }} value={athleteId ?? ''} onChange={(e) => open(e.target.value || null)} aria-label="Atleta">
            <option value="">Todas as atletas</option>
            {athletes.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        )}
        <span className="spacer" />
        {usingEmulators && <span className="chip blue">emulador</span>}
        <span className="user">
          {user.photoURL && <img src={user.photoURL} alt="" referrerPolicy="no-referrer" />}
          <span>{user.displayName ?? user.email}</span>
          <button className="btn ghost small" type="button" onClick={() => void signOut(auth)}>
            Sair
          </button>
        </span>
      </header>
      <main>
        {athletes === undefined ? (
          <p className="empty">Carregando…</p>
        ) : athlete ? (
          <AthletePage key={athlete.id} user={user} athlete={athlete} onLeft={() => open(null)} />
        ) : (
          <HomePage user={user} athletes={athletes} onOpen={open} />
        )}
      </main>
    </>
  )
}
