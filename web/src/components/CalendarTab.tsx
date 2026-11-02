import { useState } from 'react'
import { addDays, DAYS_SHORT, dayLabel, dayOfWeek, isoDay, parseDay, shortDate, weekStart } from '../lib/format'
import { dateChoice, setDate, type DateChoice } from '../sync/edits'
import { LIMITS, type DayDoc, type PlanSnapshot } from '../sync/types'
import { CommitInput } from './ui'
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
        Escolha o treino de uma data específica, ou marque feriados e dias em que a academia não abre. Treino que cair num dia
        fechado vai para o recálculo do celular, que nunca usa esses dias.
      </p>

      <ClosedWeekdays plan={plan} />

      {Array.from({ length: WEEKS }, (_, week) => (
        <section key={week} style={{ marginBottom: 22 }}>
          <p className="section-label">{week === 0 ? 'Esta semana' : week === 1 ? 'Semana que vem' : `Semana de ${shortDate(addDays(start, week * 7))}`}</p>
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
        <input className="input" type="date" min={today} value={extra} onChange={(e) => setExtra(e.target.value)} aria-label="Data" style={{ maxWidth: 200 }} />
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
    <p className="small faint" style={{ marginBottom: 18 }}>
      {closed.length === 0
        ? 'Nenhum dia da semana com a academia fechada. Marque na aba Semana (ex.: domingo).'
        : `Academia fechada toda semana: ${closed.join(', ').toLowerCase()}. Mude na aba Semana.`}
    </p>
  )
}

function DateCell({ plan, date, today, real, edit, long }: { plan: PlanSnapshot; date: string; today: string; real?: DayDoc; edit: Edit; long?: boolean }) {
  const day = parseDay(date)
  const weekday = dayOfWeek(day)
  const past = date < today
  const weekClosed = plan.week[String(weekday)]?.closed === true
  const choice = dateChoice(plan, date)
  const baseId = plan.week[String(weekday)]?.templateId ?? null
  const base = baseId ? plan.templates[baseId] : undefined
  const templates = Object.entries(plan.templates).sort(([, a], [, b]) => a.code.localeCompare(b.code))
  const value = choice.kind === 'plan' ? 'plan' : choice.kind === 'closed' ? 'closed' : choice.templateId === null ? 'rest' : `t:${choice.templateId}`

  const pick = (v: string) => {
    const next: DateChoice =
      v === 'plan' ? { kind: 'plan' } : v === 'closed' ? { kind: 'closed', label: '' } : v === 'rest' ? { kind: 'template', templateId: null } : { kind: 'template', templateId: v.slice(2) }
    edit((p) => setDate(p, date, next))
  }

  return (
    <section
      className={`day${date === today ? ' today' : ''}${choice.kind === 'closed' || weekClosed ? ' closed' : ''}${choice.kind === 'template' ? ' chosen' : ''}`}
      aria-label={dayLabel(day)}
      style={past ? { opacity: 0.5 } : undefined}
    >
      <div className="row between">
        <span className="day-name">{long ? dayLabel(day) : DAYS_SHORT[weekday - 1]}</span>
        {!long && <span className="small faint">{shortDate(day)}</span>}
      </div>
      {weekClosed ? (
        <span className="day-rest">Academia fechada</span>
      ) : past ? (
        <span className="small faint">{choice.kind === 'closed' ? choice.label || 'Fechada' : 'Já passou'}</span>
      ) : (
        <>
          <select className="select" aria-label={`Treino de ${dayLabel(day)}`} value={value} onChange={(e) => pick(e.target.value)}>
            <option value="plan">Plano · {base ? `${base.code} ${base.name}` : 'Descanso'}</option>
            {templates.map(([id, t]) => (
              <option key={id} value={`t:${id}`}>
                {t.code} · {t.name}
              </option>
            ))}
            <option value="rest">Descanso</option>
            <option value="closed">Academia fechada / feriado</option>
          </select>
          {choice.kind === 'closed' && (
            <CommitInput
              className="input"
              value={choice.label}
              placeholder="Nome (ex.: Finados)"
              maxLength={LIMITS.label}
              aria-label={`Nome do feriado de ${dayLabel(day)}`}
              onCommit={(label) => edit((p) => setDate(p, date, { kind: 'closed', label }))}
            />
          )}
          {choice.kind === 'template' && <span className="small" style={{ color: 'var(--lime)' }}>Só nesta data</span>}
        </>
      )}
      {real && (
        <div className="real">
          <span className="row wrap" style={{ gap: 6 }}>
            {statusChip(real, today)}
            <span>{real.status === 'REST' ? 'Descanso' : real.name}</span>
          </span>
        </div>
      )}
    </section>
  )
}
