import { useEffect, useState } from 'react'
import type { User } from 'firebase/auth'
import { ExercisesTab } from '../components/ExercisesTab'
import { HistoryTab } from '../components/HistoryTab'
import { TemplatesTab } from '../components/TemplatesTab'
import { WeekTab } from '../components/WeekTab'
import { WeightTab } from '../components/WeightTab'
import { Confirm } from '../components/ui'
import { leaveAthlete, useDays, usePlan, useWeights, type Athlete, type PlanState } from '../lib/data'
import { relative } from '../lib/format'

const TABS = ['Semana', 'Treinos', 'Exercícios', 'Histórico', 'Peso'] as const
type Tab = (typeof TABS)[number]

export function AthletePage({ user, athlete, onLeft }: { user: User; athlete: Athlete; onLeft: () => void }) {
  const plan = usePlan(athlete.id)
  const days = useDays(athlete.id)
  const weights = useWeights(athlete.id)
  const [tab, setTab] = useState<Tab>(() => (TABS as readonly string[]).includes(sessionStorage.getItem('tab') ?? '') ? (sessionStorage.getItem('tab') as Tab) : 'Semana')
  const [leaving, setLeaving] = useState(false)
  const [, tick] = useState(0)

  useEffect(() => {
    try {
      sessionStorage.setItem('tab', tab)
    } catch {
      /* sem armazenamento: só não lembra a aba */
    }
  }, [tab])

  // "Sincronizado há X min" anda sozinho.
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 30_000)
    return () => clearInterval(id)
  }, [])

  if (athlete.lost) {
    return (
      <div className="card empty stack" style={{ alignItems: 'center' }}>
        <p>{athlete.name} desconectou o painel ou removeu você. Para voltar, peça um código novo no app dela.</p>
        <button className="btn" type="button" onClick={() => void leaveAthlete(user, athlete.id, true).then(onLeft)}>
          Tirar da lista
        </button>
      </div>
    )
  }

  return (
    <>
      <div className="athlete-head">
        <h1>{athlete.name}</h1>
        <div className="status">
          <SyncStatus athlete={athlete} plan={plan} />
        </div>
        <span className="spacer" style={{ flex: 1 }} />
        <button className="btn ghost small" type="button" onClick={() => setLeaving(true)}>
          Parar de acompanhar
        </button>
      </div>

      <nav className="tabs" role="tablist" aria-label="Seções">
        {TABS.map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setTab(t)}>
            {t}
          </button>
        ))}
      </nav>

      {plan.error && plan.save === 'error' && <p className="notice orange">Não deu para salvar: {plan.error}. A próxima mudança tenta de novo.</p>}

      <div role="tabpanel" aria-label={tab}>
        {tab === 'Histórico' ? (
          <HistoryTab days={days} />
        ) : tab === 'Peso' ? (
          <WeightTab points={weights} />
        ) : plan.status === 'loading' ? (
          <p className="empty">Carregando o plano…</p>
        ) : plan.status === 'denied' ? (
          <p className="card empty">Sem acesso ao plano. A atleta pode ter removido você.</p>
        ) : plan.status === 'empty' || !plan.plan ? (
          <p className="card empty">
            Esperando o celular da {athlete.name} mandar o plano pela primeira vez. Peça para ela abrir o app com internet.
          </p>
        ) : tab === 'Semana' ? (
          <WeekTab plan={plan.plan} days={days} edit={plan.edit} />
        ) : tab === 'Treinos' ? (
          <TemplatesTab plan={plan.plan} edit={plan.edit} />
        ) : (
          <ExercisesTab plan={plan.plan} edit={plan.edit} />
        )}
      </div>

      {leaving && (
        <Confirm
          title={`Parar de acompanhar ${athlete.name}?`}
          body="Você deixa de ver e editar o treino dela. O plano continua no celular dela; para voltar, ela gera outro código."
          action="Parar de acompanhar"
          onConfirm={() => void leaveAthlete(user, athlete.id, false).then(onLeft)}
          onClose={() => setLeaving(false)}
        />
      )}
    </>
  )
}

/** Salvo no painel? Já chegou no celular? */
function SyncStatus({ athlete, plan }: { athlete: Athlete; plan: PlanState }) {
  const saving = plan.save === 'pending' || plan.save === 'saving'
  const phoneBehind = plan.updatedBy === 'web' && plan.savedAt && (!athlete.lastSyncAt || athlete.lastSyncAt < plan.savedAt)
  return (
    <>
      {saving ? (
        <span className="chip">Salvando…</span>
      ) : plan.save === 'error' ? (
        <span className="chip orange">Erro ao salvar</span>
      ) : plan.save === 'saved' ? (
        <span className="chip lime">Salvo</span>
      ) : null}
      {phoneBehind ? (
        <span className="chip blue" title="O celular busca as mudanças a cada 15 min, ou na hora com o app aberto.">
          Aguardando o celular
        </span>
      ) : athlete.lastSyncAt ? (
        <span className="chip" title={athlete.appVersion ? `App ${athlete.appVersion}` : undefined}>
          Celular sincronizou {relative(athlete.lastSyncAt)}
        </span>
      ) : (
        <span className="chip orange">Celular ainda não sincronizou</span>
      )}
    </>
  )
}
