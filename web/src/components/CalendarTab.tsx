import { useState } from 'react'
import { addDays, DAYS_SHORT, dayLabel, dayOfWeek, isoDay, parseDay, shortDate, weekStart } from '../lib/format'
import { dateChoice, setDate, type DateChoice } from '../sync/edits'
import { LIMITS, type DayDoc, type PlanSnapshot } from '../sync/types'
import { CommitInput, Menu, MenuItem } from './ui'
import { statusChip } from './WeekTab'

type Edit = (change: (p: PlanSnapshot) => PlanSnapshot) => void

const WEEKS = 5

/**
 * Calendário por data: treino escolhido para um dia específico, feriados e dias em que a academia fecha.
 * O celular reorganiza a semana sozinho (o treino de um dia fechado entra no recálculo).
 */
export function CalendarTab({ plan, days, edit }: { plan: PlanSnapshot; days: DayDoc[] | undefined; edit: Edit }) {
  const now = new Date()
  const today = isoDay(now)
  const start = weekStart(now)
  const end = isoDay(addDays(start, WEEKS * 7 - 1))
  const real = new Map((days ?? []).map((d) => [d.date, d]))
  const [extra, setExtra] = useState('')

  // Datas marcadas depois das semanas mostradas (ex.: Natal).
  const later = Object.keys(plan.dates)
    .filter((date) => date > end && dateChoice(plan, date).kind !== 'plan')
    .sort()

  return (
    <>
      <p className="muted" style={{ marginTop: 0 }}>
        Cada dia mostra o que vai acontecer. Use “Trocar” para escolher o treino de uma data ou marcar um feriado; o celular encaixa o
        treino de um dia fechado em outro dia da semana.
      </p>

      <div className="legend small muted">
        <span>
          <i className="dot lime" /> Só nesta data
        </span>
        <span>
          <i className="dot blue" /> Mudado pelo celular
        </span>
        <span>
          <i className="dot stripes" /> Academia fechada
        </span>
        <ClosedWeekdays plan={plan} />
      </div>

      {Array.from({ length: WEEKS }, (_, week) => (
        <section key={week} style={{ marginBottom: 22 }}>
          <p className="section-label">
            {week === 0 ? 'Esta semana' : week === 1 ? 'Semana que vem' : `Semana de ${shortDate(addDays(start, week * 7))}`}
          </p>
          <div className="week calendar">
            {Array.from({ length: 7 }, (_, i) => {
              const date = isoDay(addDays(start, week * 7 + i))
              return <DateCell key={date} plan={plan} date={date} today={today} real={real.get(date)} edit={edit} />
            })}
          </div>
        </section>
      ))}

      <p className="section-label">Outras datas</p>
      <div className="row wrap" style={{ gap: 8, marginBottom: 12 }}>
        <input
          className="input"
          type="date"
          min={today}
          value={extra}
          onChange={(e) => setExtra(e.target.value)}
          aria-label="Data"
          style={{ maxWidth: 200 }}
        />
        <button
          className="btn small"
          type="button"
          disabled={!/^\d{4}-\d{2}-\d{2}$/.test(extra) || extra < today}
          onClick={() => {
            edit((p) => setDate(p, extra, { kind: 'closed', label: '' }))
            setExtra('')
          }}
        >
          Marcar como fechada
        </button>
      </div>
      {later.length === 0 ? (
        <p className="small faint">Nenhuma data marcada depois das próximas {WEEKS} semanas.</p>
      ) : (
        <div className="week calendar">
          {later.map((date) => (
            <DateCell key={date} plan={plan} date={date} today={today} edit={edit} long />
          ))}
        </div>
      )}
    </>
  )
}

function ClosedWeekdays({ plan }: { plan: PlanSnapshot }) {
  const closed = DAYS_SHORT.filter((_, i) => plan.week[String(i + 1)]?.closed)
  return (
    <span className="faint">
      {closed.length === 0
        ? 'Nenhum dia fechado toda semana (aba Semana)'
        : `Fechada toda semana: ${closed.join(', ').toLowerCase()} (aba Semana)`}
    </span>
  )
}

/** O que o card mostra em destaque: o que vai acontecer no dia, e de onde veio. */
interface Effective {
  kind: 'workout' | 'rest' | 'closed'
  title: string
  code?: string
  origin: string
  tone: '' | 'lime' | 'blue'
}

function effective(plan: PlanSnapshot, date: string, real: DayDoc | undefined): Effective {
  const weekday = dayOfWeek(parseDay(date))
  if (plan.week[String(weekday)]?.closed)
    return {
      kind: 'closed',
      title: 'Academia fechada',
      origin: 'Toda semana',
      tone: '',
    }
  const choice = dateChoice(plan, date)
  if (choice.kind === 'closed')
    return {
      kind: 'closed',
      title: choice.label || 'Feriado',
      origin: 'Academia fechada só nesta data',
      tone: 'lime',
    }

  const baseId = plan.week[String(weekday)]?.templateId ?? null
  const base = baseId ? plan.templates[baseId] : undefined
  const workout = (templateId: string | null, origin: string, tone: Effective['tone']): Effective => {
    const t = templateId ? plan.templates[templateId] : undefined
    return t ? { kind: 'workout', title: t.name, code: t.code, origin, tone } : { kind: 'rest', title: 'Descanso', origin, tone }
  }

  if (choice.kind === 'template') {
    // Escolhido no painel e o celular ainda não pegou: mostra a escolha e avisa.
    const pending = real && real.status !== 'DONE' && real.status !== 'PARTIAL' && (real.templateId ?? null) !== choice.templateId
    return workout(choice.templateId, pending ? 'Só nesta data · aguardando o celular' : 'Só nesta data', 'lime')
  }
  if (real) {
    // Nesta semana vale o que está no celular (o recálculo pode ter mudado o dia).
    const changed = real.special || (real.status === 'REST' ? baseId !== null : real.templateId !== baseId)
    const origin = changed ? `Mudado pelo celular · plano: ${base?.name ?? 'Descanso'}` : 'Plano base'
    if (real.status === 'REST')
      return {
        kind: 'rest',
        title: 'Descanso',
        origin,
        tone: changed ? 'blue' : '',
      }
    return {
      kind: 'workout',
      title: real.name,
      code: real.templateId ? plan.templates[real.templateId]?.code : undefined,
      origin,
      tone: changed ? 'blue' : '',
    }
  }
  return workout(baseId, 'Plano base', '')
}

function DateCell({
  plan,
  date,
  today,
  real,
  edit,
  long,
}: {
  plan: PlanSnapshot
  date: string
  today: string
  real?: DayDoc
  edit: Edit
  long?: boolean
}) {
  const day = parseDay(date)
  const weekday = dayOfWeek(day)
  const past = date < today
  const isToday = date === today
  const weekClosed = plan.week[String(weekday)]?.closed === true
  const choice = dateChoice(plan, date)
  const shown = effective(plan, date, real)
  const baseId = plan.week[String(weekday)]?.templateId ?? null
  const base = baseId ? plan.templates[baseId] : undefined
  const templates = Object.entries(plan.templates).sort(([, a], [, b]) => a.code.localeCompare(b.code))
  // Status do celular só quando diz algo além de "planejado".
  const chip = real && (real.status !== 'PLANNED' || real.movedTo || past) ? statusChip(real, today) : null
  const pick = (next: DateChoice) => edit((p) => setDate(p, date, next))

  return (
    <section
      className={['day', isToday && 'today', past && 'past', shown.kind === 'closed' && 'closed', shown.tone && `accent-${shown.tone}`]
        .filter(Boolean)
        .join(' ')}
      aria-label={dayLabel(day)}
    >
      <div className="day-head">
        <span className="day-name">{long ? dayLabel(day) : DAYS_SHORT[weekday - 1]}</span>
        {isToday ? <span className="chip lime">Hoje</span> : !long && <span className="small faint">{shortDate(day)}</span>}
      </div>

      <div className="day-main">
        <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
          {shown.kind === 'workout' && <span className="code-badge mini">{shown.code ?? '·'}</span>}
          <span
            className={`day-title${shown.kind === 'rest' ? ' quiet' : ''}${real?.status === 'MISSED' && shown.kind === 'workout' ? ' gone' : ''}`}
          >
            {shown.title}
          </span>
        </div>
        <span className={`origin ${shown.tone}`}>{shown.origin}</span>
      </div>

      {chip && (
        <div className="row wrap" style={{ gap: 6 }}>
          {chip}
        </div>
      )}

      {choice.kind === 'closed' && !past && (
        <CommitInput
          className="input"
          value={choice.label}
          placeholder="Nome do feriado"
          maxLength={LIMITS.label}
          aria-label={`Nome do feriado de ${dayLabel(day)}`}
          onCommit={(label) => pick({ kind: 'closed', label })}
        />
      )}

      {!past && !weekClosed && (
        <div className="day-foot">
          <Menu label="Trocar" ariaLabel={`Trocar o treino de ${dayLabel(day)}`}>
            {(close) => (
              <>
                <MenuItem selected={choice.kind === 'plan'} onSelect={() => (close(), pick({ kind: 'plan' }))}>
                  Seguir o plano ({base?.name ?? 'Descanso'})
                </MenuItem>
                <div className="menu-sep" />
                {templates.map(([id, t]) => (
                  <MenuItem
                    key={id}
                    selected={choice.kind === 'template' && choice.templateId === id}
                    onSelect={() => (close(), pick({ kind: 'template', templateId: id }))}
                  >
                    <span className="code-badge mini">{t.code}</span>
                    {t.name}
                  </MenuItem>
                ))}
                <MenuItem
                  selected={choice.kind === 'template' && choice.templateId === null}
                  onSelect={() => (close(), pick({ kind: 'template', templateId: null }))}
                >
                  <span className="code-badge mini muted">–</span>
                  Descanso
                </MenuItem>
                <div className="menu-sep" />
                <MenuItem
                  selected={choice.kind === 'closed'}
                  onSelect={() => (
                    close(),
                    pick({
                      kind: 'closed',
                      label: choice.kind === 'closed' ? choice.label : '',
                    })
                  )}
                >
                  Academia fechada / feriado
                </MenuItem>
              </>
            )}
          </Menu>
        </div>
      )}
    </section>
  )
}
