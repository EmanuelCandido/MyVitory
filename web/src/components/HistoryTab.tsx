import { useMemo, useState } from 'react'
import { historyStats, loadHistory, trainedExercises } from '../lib/analysis'
import { dayLabel, isoDay, kg, parseDay, plural, shortDate, weekStart } from '../lib/format'
import type { DayDoc } from '../sync/types'
import { LineChart } from './LineChart'
import { useCountUp } from './ui'
import { statusChip } from './WeekTab'

/** O que a atleta fez: números das últimas semanas, evolução de carga e cada dia com séries e comentário. */
export function HistoryTab({ days }: { days: DayDoc[] | undefined }) {
  const exercises = useMemo(() => trainedExercises(days ?? []), [days])
  const [exerciseId, setExerciseId] = useState<string | null>(null)
  const chosen = exerciseId ?? exercises[0]?.id ?? null

  if (!days) return <p className="empty">Carregando o histórico…</p>
  if (days.length === 0) return <p className="card empty">Ainda não chegou nenhum treino do celular. Os dias aparecem aqui depois do primeiro sync.</p>

  return <History days={days} exercises={exercises} chosen={chosen} onChoose={setExerciseId} />
}

function History({ days, exercises, chosen, onChoose }: {
  days: DayDoc[]
  exercises: ReturnType<typeof trainedExercises>
  chosen: string | null
  onChoose: (id: string) => void
}) {
  const today = isoDay(new Date())
  const stats = useMemo(() => historyStats(days), [days])
  const points = useMemo(() => (chosen ? loadHistory(days, chosen) : []), [days, chosen])
  const done = useCountUp(stats.doneLast28)
  const percent = useCountUp(stats.plannedLast28 ? Math.round((stats.doneLast28 / stats.plannedLast28) * 100) : 0)
  const missed = useCountUp(stats.missedLast28)
  const streak = useCountUp(stats.streakWeeks)

  // Agrupa por semana (segunda a domingo), da mais recente para a mais antiga; só o que já aconteceu.
  const past = days.filter((d) => d.date <= today)
  const weeks = new Map<string, DayDoc[]>()
  past.forEach((d) => {
    const key = isoDay(weekStart(parseDay(d.date)))
    weeks.set(key, [...(weeks.get(key) ?? []), d])
  })

  return (
    <div>
      <div className="stats">
        <div className="stat">
          <b>
            {done}
            <span className="faint" style={{ fontSize: 20 }}>
              /{stats.plannedLast28}
            </span>
          </b>
          <span>treinos feitos nos últimos 28 dias</span>
        </div>
        <div className="stat">
          <b>{percent}%</b>
          <span>de frequência</span>
        </div>
        <div className={`stat${stats.missedLast28 > 0 ? ' flame' : ''}`}>
          <b>{missed}</b>
          <span>{stats.missedLast28 === 1 ? 'treino não feito' : 'treinos não feitos'}</span>
        </div>
        <div className="stat">
          <b>{streak}</b>
          <span>{stats.streakWeeks === 1 ? 'semana seguida treinando' : 'semanas seguidas treinando'}</span>
        </div>
      </div>

      {exercises.length > 0 && chosen && (
        <section className="card" style={{ marginBottom: 18 }}>
          <div className="row between wrap" style={{ marginBottom: 8 }}>
            <h3 style={{ margin: 0 }}>Evolução da carga</h3>
            <select className="select" style={{ width: 'auto', minWidth: 240 }} value={chosen} onChange={(e) => onChoose(e.target.value)} aria-label="Exercício">
              {exercises.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name} ({e.count})
                </option>
              ))}
            </select>
          </div>
          <LineChart
            key={chosen}
            ariaLabel="Maior carga por treino"
            unit="kg"
            points={points.map((p) => ({ label: shortDate(parseDay(p.date)), value: p.topKg, detail: `reps ${p.reps}` }))}
          />
        </section>
      )}

      {[...weeks.entries()].map(([start, list]) => {
        const done = list.filter((d) => d.status === 'DONE' || d.status === 'PARTIAL').length
        const planned = list.filter((d) => d.status !== 'REST').length
        return (
          <section key={start} className="history-week">
            <p className="section-label">
              Semana de {shortDate(parseDay(start))} · {done}/{planned} feitos
            </p>
            {list
              .filter((d) => d.status !== 'REST')
              .map((d) => (
                <DayRow key={d.date} day={d} today={today} />
              ))}
          </section>
        )
      })}
    </div>
  )
}

function DayRow({ day, today }: { day: DayDoc; today: string }) {
  const minutes = day.startedAt && day.finishedAt ? Math.round((day.finishedAt - day.startedAt) / 60_000) : null
  const doneSets = day.exercises.reduce((n, e) => n + e.sets.filter((s) => s.done).length, 0)
  const hasSets = day.status === 'DONE' || day.status === 'PARTIAL'
  return (
    <details className="history-day">
      <summary>
        <span className="date">{dayLabel(parseDay(day.date))}</span>
        {statusChip(day, today)}
        <strong>{day.name}</strong>
        <span className="small muted">
          {hasSets && doneSets > 0 && plural(doneSets, 'série', 'séries')}
          {minutes !== null && ` · ${minutes} min`}
          {day.movedTo && ` · levado para ${dayLabel(parseDay(day.movedTo))}`}
        </span>
      </summary>
      {day.comment && <div className="comment">“{day.comment}”</div>}
      {day.exercises.length > 0 && (
        <table>
          <tbody>
            {day.exercises.map((e, i) => (
              <tr key={`${e.exerciseId}-${i}`}>
                <td>
                  {e.name}
                  {e.note && <div className="small faint">{e.note}</div>}
                </td>
                <td>{describeSets(e.sets, hasSets)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </details>
  )
}

/** "40 kg × 10, 10, 8" (feitas) ou "3 × 10" (planejado). */
function describeSets(sets: DayDoc['exercises'][number]['sets'], done: boolean): string {
  if (!done) return sets.length ? `${sets.length} × ${sets[0].reps}` : ''
  const made = sets.filter((s) => s.done)
  if (made.length === 0) return 'não feito'
  const top = Math.max(...made.map((s) => s.kg ?? 0))
  const reps = made.map((s) => s.reps).join(', ')
  return top > 0 ? `${kg(top)} kg × ${reps}` : `${reps} reps`
}
