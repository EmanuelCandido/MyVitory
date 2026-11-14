import { lazy, Suspense, useEffect, useState } from 'react'
import type { User } from 'firebase/auth'
import { WeekTab } from '../components/WeekTab'
import { Confirm, TabBar, Ticket, useCountUp, WarnBadge } from '../components/ui'
import { historyStats, weekOverview } from '../lib/analysis'
import { leaveAthlete, useDays, usePlan, useWeights, type Athlete, type PlanState } from '../lib/data'
import { daysAgo, relative } from '../lib/format'
import type { DayDoc } from '../sync/types'

const TABS = ['Semana', 'Calendário', 'Treinos', 'Exercícios', 'Histórico', 'Peso'] as const
type Tab = (typeof TABS)[number]

// Só a Semana (aba de abertura) vem no pacote inicial; as outras chegam sob demanda e são pré-buscadas no tempo ocioso.
const loaders = {
  calendar: () => import('../components/CalendarTab'),
  templates: () => import('../components/TemplatesTab'),
  exercises: () => import('../components/ExercisesTab'),
  history: () => import('../components/HistoryTab'),
  weight: () => import('../components/WeightTab'),
}
const CalendarTab = lazy(() => loaders.calendar().then((m) => ({ default: m.CalendarTab })))
const TemplatesTab = lazy(() => loaders.templates().then((m) => ({ default: m.TemplatesTab })))
const ExercisesTab = lazy(() => loaders.exercises().then((m) => ({ default: m.ExercisesTab })))
const HistoryTab = lazy(() => loaders.history().then((m) => ({ default: m.HistoryTab })))
const WeightTab = lazy(() => loaders.weight().then((m) => ({ default: m.WeightTab })))

/** Baixa as abas quando o navegador está à toa: trocar de aba fica instantâneo, sem pesar na abertura. */
function usePrefetchTabs() {
  useEffect(() => {
    const idle = window.requestIdleCallback ?? ((fn: () => void) => window.setTimeout(fn, 1500))
    const cancel = window.cancelIdleCallback ?? window.clearTimeout
    const id = idle(() => Object.values(loaders).forEach((load) => void load().catch(() => undefined)))
    return () => cancel(id)
  }, [])
}

/** Sete cards fantasmas no lugar da semana enquanto o plano chega. */
function WeekSkeleton() {
  return (
    <div className="week" aria-label="Carregando o plano" role="status">
      {Array.from({ length: 7 }, (_, i) => (
        <div key={i} className="day skeleton" />
      ))}
    </div>
  )
}

export function AthletePage({ user, athlete, onLeft }: { user: User; athlete: Athlete; onLeft: () => void }) {
  const plan = usePlan(athlete.id)
  const days = useDays(athlete.id)
  const weights = useWeights(athlete.id)
  const [tab, setTab] = useState<Tab>(() => (TABS as readonly string[]).includes(sessionStorage.getItem('tab') ?? '') ? (sessionStorage.getItem('tab') as Tab) : 'Semana')
  const [leaving, setLeaving] = useState(false)
  usePrefetchTabs()

  useEffect(() => {
    try {
      sessionStorage.setItem('tab', tab)
    } catch {
      /* sem armazenamento: só não lembra a aba */
    }
  }, [tab])

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
        <button className="btn small" type="button" onClick={() => setLeaving(true)}>
          Parar de acompanhar
        </button>
      </div>

      <Overview days={days} />
      <TabBar tabs={TABS} value={tab} onChange={setTab} label="Seções" />

      {plan.error && plan.save === 'error' && <p className="notice flame">Não deu para salvar: {plan.error}. A próxima mudança tenta de novo.</p>}

      {/* key: o conteúdo da aba nova sobe 8 px esmaecendo. */}
      <div role="tabpanel" aria-label={tab} key={tab} className="tab-panel">
        <Suspense fallback={<p className="empty">Carregando…</p>}>
        {tab === 'Histórico' ? (
          <HistoryTab days={days} />
        ) : tab === 'Peso' ? (
          <WeightTab points={weights} />
        ) : plan.status === 'loading' ? (
          tab === 'Semana' || tab === 'Calendário' ? <WeekSkeleton /> : <p className="empty">Carregando o plano…</p>
        ) : plan.status === 'denied' ? (
          <p className="card empty">Sem acesso ao plano. A atleta pode ter removido você.</p>
        ) : plan.status === 'empty' || !plan.plan ? (
          <p className="card empty">
            Esperando o celular da {athlete.name} mandar o plano pela primeira vez. Peça para ela abrir o app com internet.
          </p>
        ) : tab === 'Semana' ? (
          <WeekTab plan={plan.plan} days={days} edit={plan.edit} />
        ) : tab === 'Calendário' ? (
          <CalendarTab plan={plan.plan} days={days} edit={plan.edit} />
        ) : tab === 'Treinos' ? (
          <TemplatesTab plan={plan.plan} edit={plan.edit} />
        ) : (
          <ExercisesTab plan={plan.plan} edit={plan.edit} />
        )}
        </Suspense>
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

/**
 * Resumo antes do detalhe: a pergunta do treinador ao abrir a atleta é "ela está treinando?".
 * Etiquetas como o contador da referência: número grande, unidade pequena.
 */
function Overview({ days }: { days: DayDoc[] | undefined }) {
  if (!days || days.length === 0) return null
  const week = weekOverview(days)
  const stats = historyStats(days)
  return <OverviewTickets week={week} stats={stats} />
}

function OverviewTickets({ week, stats }: { week: ReturnType<typeof weekOverview>; stats: ReturnType<typeof historyStats> }) {
  const done = useCountUp(week.done)
  const percent = useCountUp(stats.plannedLast28 ? Math.round((stats.doneLast28 / stats.plannedLast28) * 100) : 0)
  const streak = useCountUp(stats.streakWeeks)
  const frequency = stats.plannedLast28 ? `${percent}%` : '—'
  let i = 0
  return (
    <div className="strip tilt kpis" role="list" aria-label="Resumo da atleta">
      <span role="listitem">
        <Ticket value={`${done}/${week.planned}`} unit="nesta semana" stamp={i++} />
      </span>
      {week.missed > 0 && (
        <span role="listitem" className="row" style={{ gap: 4 }}>
          <WarnBadge size={22} />
          <Ticket value={week.missed} unit={week.missed === 1 ? 'ficou para trás' : 'ficaram para trás'} stamp={i++} />
        </span>
      )}
      <span role="listitem">
        <Ticket value={frequency} unit="frequência 28 dias" tone="dim" title={`${stats.doneLast28} de ${stats.plannedLast28} treinos`} stamp={i++} />
      </span>
      <span role="listitem">
        <Ticket value={streak} unit={stats.streakWeeks === 1 ? 'semana seguida' : 'semanas seguidas'} tone={stats.streakWeeks > 0 ? 'volt' : 'dim'} stamp={i++} />
      </span>
      {week.lastTrained && (
        <span role="listitem">
          <Ticket value={daysAgo(week.lastTrained)} unit="último treino" tone="paper" stamp={i++} />
        </span>
      )}
    </div>
  )
}

/** Salvo no painel? Já chegou no celular? */
function SyncStatus({ athlete, plan }: { athlete: Athlete; plan: PlanState }) {
  // "Sincronizou há X min" anda sozinho: o relógio re-renderiza só este chip, não a página da atleta.
  const [, tick] = useState(0)
  useEffect(() => {
    const id = setInterval(() => tick((n) => n + 1), 30_000)
    return () => clearInterval(id)
  }, [])
  const saving = plan.save === 'pending' || plan.save === 'saving'
  const phoneBehind = plan.updatedBy === 'web' && plan.savedAt && (!athlete.lastSyncAt || athlete.lastSyncAt < plan.savedAt)
  return (
    <>
      {saving ? (
        <span className="chip">
          <span className="pulse" aria-hidden="true" />
          Salvando…
        </span>
      ) : plan.save === 'error' ? (
        <span className="chip flame">Erro ao salvar</span>
      ) : plan.save === 'saved' ? (
        <span className="chip volt stamp">Salvo</span>
      ) : null}
      {phoneBehind ? (
        <span className="chip sky" title="O celular busca as mudanças a cada 15 min, ou na hora com o app aberto.">
          Aguardando o celular
        </span>
      ) : athlete.lastSyncAt ? (
        <span className="chip" title={athlete.appVersion ? `App ${athlete.appVersion}` : undefined}>
          Celular sincronizou {relative(athlete.lastSyncAt)}
        </span>
      ) : (
        <span className="chip flame">Celular ainda não sincronizou</span>
      )}
    </>
  )
}
