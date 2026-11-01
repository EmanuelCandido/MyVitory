import { useState } from 'react'
import { GoogleAuthProvider, signInWithCredential, signInWithPopup, signInWithRedirect, type User } from 'firebase/auth'
import { auth, google, usingEmulators } from '../firebase'
import { normalizeCode, pairWithCode, type Athlete } from '../lib/data'
import { relative } from '../lib/format'

export function LoginPage() {
  const [error, setError] = useState<string | null>(null)
  return (
    <div className="center-page">
      <div className="hero">
        <span className="brand">
          <span className="brand-mark" /> MyVitory <small>painel do treinador</small>
        </span>
        <h1>Monte e acompanhe o treino pelo navegador.</h1>
        <p className="muted" style={{ margin: 0 }}>
          Mude exercícios, ordem, séries e descanso. As mudanças chegam sozinhas no app da atleta, e você vê o que ela fez.
        </p>
        <button
          className="btn primary"
          type="button"
          style={{ minHeight: 48 }}
          onClick={() =>
            signInWithPopup(auth, google).catch((e: { code?: string; message: string }) =>
              // Navegador que bloqueia a janela (comum no celular): entra pela própria página.
              e.code === 'auth/popup-blocked' ? signInWithRedirect(auth, google) : setError(e.message),
            )
          }
        >
          Entrar com Google
        </button>
        {usingEmulators && (
          <>
            <p className="notice blue small">Modo emulador: nada aqui vai para o Firebase de verdade.</p>
            <button
              className="btn"
              type="button"
              onClick={() =>
                // O emulador aceita um token falso do Google: entra direto, sem a janela de login.
                signInWithCredential(
                  auth,
                  GoogleAuthProvider.credential(JSON.stringify({ sub: 'treinador-teste', email: 'treinador@teste.dev', email_verified: true, name: 'Treinador de teste' })),
                ).catch((e: Error) => setError(e.message))
              }
            >
              Entrar como treinador de teste
            </button>
          </>
        )}
        {error && <p className="notice orange small">Não deu para entrar: {error}</p>}
      </div>
    </div>
  )
}

export function SetupPage() {
  return (
    <div className="center-page">
      <div className="hero">
        <h1>Falta configurar o Firebase</h1>
        <p className="muted">
          Copie <code>web/.env.example</code> para <code>web/.env.local</code> e preencha com a configuração do app web do seu projeto no Firebase.
          O passo a passo está em <code>docs/painel-web.md</code>.
        </p>
        <p className="muted">
          Para testar sem projeto: <code>npm run emuladores</code> e, em outro terminal, <code>npm run dev:emulador</code>.
        </p>
      </div>
    </div>
  )
}

/** Lista de atletas e o pareamento por código. */
export function HomePage({ user, athletes, onOpen }: { user: User; athletes: Athlete[]; onOpen: (id: string) => void }) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const pair = async () => {
    setBusy(true)
    setError(null)
    try {
      onOpen(await pairWithCode(user, code))
    } catch (e) {
      setError(e instanceof Error ? friendly(e.message) : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="stack" style={{ gap: 28, maxWidth: 860 }}>
      {athletes.length > 0 && (
        <section>
          <p className="section-label">Suas atletas</p>
          <div className="athlete-cards">
            {athletes.map((a) => (
              <button key={a.id} type="button" className="athlete-card" onClick={() => onOpen(a.id)}>
                <strong>{a.name}</strong>
                <span className="small muted">
                  {a.lost ? 'Sem acesso' : a.lastSyncAt ? `Celular sincronizou ${relative(a.lastSyncAt)}` : 'Celular ainda não sincronizou'}
                </span>
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="card stack" style={{ maxWidth: 520 }}>
        <h2 style={{ margin: 0 }}>{athletes.length ? 'Acompanhar outra atleta' : 'Conectar à atleta'}</h2>
        <p className="muted" style={{ margin: 0 }}>
          No app MyVitory dela: Ajustes › Painel do treinador › Conectar. O app mostra um código de 8 letras, válido por 30 minutos.
        </p>
        <form
          className="stack"
          onSubmit={(e) => {
            e.preventDefault()
            void pair()
          }}
        >
          <input
            className="input code-input"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="ABCD-EFGH"
            aria-label="Código de pareamento"
            autoComplete="off"
            maxLength={9}
          />
          <button className="btn primary" type="submit" disabled={busy || normalizeCode(code).length !== 8}>
            {busy ? 'Conectando…' : 'Conectar'}
          </button>
        </form>
        {error && <p className="notice orange small">{error}</p>}
      </section>
    </div>
  )
}

function friendly(message: string) {
  if (message.includes('permission') || message.includes('PERMISSION')) return 'O código não vale mais. Gere outro no celular.'
  return message
}
