import { useEffect, useMemo, useState } from 'react'
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import { estimateMinutes, primaryMuscles, recoveryWarnings, warningText } from '../lib/analysis'
import { muscleLabel } from '../lib/catalog'
import { addDays, DAYS, DAYS_SHORT, dayOfWeek, isoDay, parseDay, plural, shortDate, weekStart } from '../lib/format'
import { setClosedWeekday, setDay } from '../sync/edits'
import type { DayDoc, PlanSnapshot } from '../sync/types'
import { Menu, MenuItem, Ticket, Warn } from './ui'

const STATUS: Record<DayDoc['status'], { label: string; tone: string }> = {
  DONE: { label: 'Feito', tone: 'volt' },
  PARTIAL: { label: 'Parcial', tone: 'sky' },
  MISSED: { label: 'Não feito', tone: 'flame' },
  PLANNED: { label: 'Planejado', tone: '' },
  REST: { label: 'Descanso', tone: '' },
}

export function statusChip(day: DayDoc, today: string) {
  if (day.status === 'MISSED' && day.movedTo) {
    return <span className="chip sky">Movido → {DAYS_SHORT[dayOfWeek(parseDay(day.movedTo)) - 1].toLowerCase()}</span>
  }
  if (day.closed && day.status !== 'DONE' && day.status !== 'PARTIAL') return <span className="chip">{day.closed}</span>
  if (day.unavailable && day.status !== 'DONE' && day.status !== 'PARTIAL') return <span className="chip flame">Indisponível</span>
  if (day.status === 'PLANNED' && day.date < today) return <span className="chip flame">Sem registro</span>
  if (day.status === 'PLANNED' && day.date === today) return <span className="chip volt">Hoje</span>
  const s = STATUS[day.status]
  return <span className={`chip ${s.tone}`}>{s.label}</span>
}

/** O dia no celular só merece linha própria quando conta algo que o plano não diz. */
export const phoneSaysMore = (real: DayDoc) => real.special || real.status !== 'PLANNED' || real.movedTo !== null

/** Plano base da semana: arraste um treino para um dia ou use "Trocar". */
export function WeekTab({
  plan,
  days,
  edit,
}: {
  plan: PlanSnapshot
  days: DayDoc[] | undefined
  edit: (change: (p: PlanSnapshot) => PlanSnapshot) => void
}) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }), useSensor(KeyboardSensor))
  const [dragging, setDragging] = useState<string | null>(null)
  // Dia que acabou de receber um treino (arrastar ou "Trocar"): o card carimba por 700 ms.
  const [justSet, setJustSet] = useState<number | null>(null)
  useEffect(() => {
    if (justSet === null) return
    const id = window.setTimeout(() => setJustSet(null), 700)
    return () => window.clearTimeout(id)
  }, [justSet])
  const templates = useMemo(() => Object.entries(plan.templates).sort(([, a], [, b]) => a.code.localeCompare(b.code)), [plan.templates])
  const warnings = useMemo(() => recoveryWarnings(plan), [plan])
  const assign = (day: number, templateId: string | null) => {
    edit((p) => setDay(p, day, templateId))
    setJustSet(day)
  }

  const now = new Date()
  const today = isoDay(now)
  const start = weekStart(now)
  const thisWeek = new Map((days ?? []).map((d) => [d.date, d]))

  const onDragEnd = (e: DragEndEvent) => {
    setDragging(null)
    const day = e.over?.id
    if (typeof day !== 'number') return
    const templateId = String(e.active.id) === 'rest' ? null : String(e.active.id)
    // Dia com a academia fechada não recebe treino: primeiro abra o dia lá em cima.
    if (templateId !== null && plan.week[String(day)]?.closed) return
    assign(day, templateId)
  }

  return (
    <DndContext
      sensors={sensors}
      onDragStart={(e) => setDragging(String(e.active.id))}
      onDragCancel={() => setDragging(null)}
      onDragEnd={onDragEnd}
    >
      <p className="section-label">Treinos · arraste para um dia</p>
      <div className="tray">
        {templates.map(([id, t]) => (
          <TrayItem key={id} id={id} code={t.code} name={t.name} />
        ))}
        <TrayItem id="rest" code="–" name="Descanso" />
      </div>

      <div className="closed-days">
        <span className="section-label" style={{ margin: 0 }}>
          Academia fechada
        </span>
        {DAYS_SHORT.map((short, i) => {
          const closed = plan.week[String(i + 1)]?.closed === true
          return (
            <button
              key={short}
              type="button"
              className="filter-chip"
              aria-pressed={closed}
              aria-label={`Academia fechada ${DAYS[i].toLowerCase()}`}
              onClick={() => edit((p) => setClosedWeekday(p, i + 1, !closed))}
            >
              {short.toLowerCase()}
            </button>
          )
        })}
      </div>

      <p className="section-label">Plano base da semana</p>
      <div className="week">
        {DAYS.map((name, index) => {
          const day = index + 1
          const templateId = plan.week[String(day)]?.templateId ?? null
          const template = templateId ? plan.templates[templateId] : undefined
          const closed = plan.week[String(day)]?.closed === true
          const date = addDays(start, index)
          const isToday = dayOfWeek(now) === day
          const real = thisWeek.get(isoDay(date))
          const dayWarnings = warnings.filter((w) => w.day === day)
          return (
            <DayCard key={day} day={day} today={isToday} closed={closed} justSet={justSet === day}>
              {isToday && (
                <span className="day-tag">
                  <Ticket value="Hoje" tone="volt" small />
                </span>
              )}
              <div className="day-head">
                <span className="day-name">{name}</span>
                <span className="small faint">{shortDate(date)}</span>
              </div>

              {closed ? (
                <div className="day-main">
                  <span className="day-title">Academia fechada</span>
                  <span className="small muted">O recálculo nunca põe treino aqui.</span>
                </div>
              ) : template ? (
                <div className="day-main">
                  <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
                    <span className="code-badge">{template.code}</span>
                    <span className="day-title">{template.name}</span>
                  </div>
                  <span className="small muted">
                    {plural(template.items.length, 'exercício', 'exercícios')} · ~{estimateMinutes(template)} min
                  </span>
                  <span className="small faint">{primaryMuscles(plan, template).map(muscleLabel).join(', ')}</span>
                </div>
              ) : (
                <div className="day-main">
                  <span className="day-title quiet">Descanso</span>
                </div>
              )}

              {dayWarnings.map((w) => (
                <Warn key={w.muscle}>{warningText(w)}</Warn>
              ))}

              <div className="day-actions">
                {!closed && (
                  <Menu label="Trocar" ariaLabel={`Trocar o treino de ${name.toLowerCase()}`}>
                    {(close) => (
                      <>
                        {templates.map(([id, t]) => (
                          <MenuItem key={id} selected={id === templateId} onSelect={() => (close(), assign(day, id))}>
                            <span className="code-badge mini">{t.code}</span>
                            {t.name}
                          </MenuItem>
                        ))}
                        <MenuItem selected={templateId === null} onSelect={() => (close(), assign(day, null))}>
                          <span className="code-badge mini muted">–</span>
                          Descanso
                        </MenuItem>
                      </>
                    )}
                  </Menu>
                )}

                {real && phoneSaysMore(real) && (
                  <div className="day-foot">
                    <span className="faint">Esta semana no celular</span>
                    <span className="row wrap" style={{ gap: 6 }}>
                      {statusChip(real, today)}
                      <span>{real.status === 'REST' ? 'Descanso' : real.name}</span>
                    </span>
                    {real.comment && <span className="muted">“{real.comment}”</span>}
                  </div>
                )}
              </div>
            </DayCard>
          )
        })}
      </div>
      <p className="small faint" style={{ marginTop: 14 }}>
        Mudanças no plano base valem para os próximos dias que ainda não começaram. O rodapé de cada dia só aparece quando o celular mudou
        algo nesta semana (recálculo ou troca só nesta semana). Feriados e treinos de uma data específica ficam na aba Calendário.
      </p>

      <DragOverlay>
        {dragging && (
          <div className="tray-item drag-overlay">
            <span className="code-badge">{dragging === 'rest' ? '–' : plan.templates[dragging]?.code}</span>
            {dragging === 'rest' ? 'Descanso' : plan.templates[dragging]?.name}
          </div>
        )}
      </DragOverlay>
    </DndContext>
  )
}

function TrayItem({ id, code, name }: { id: string; code: string; name: string }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id,
  })
  return (
    <div
      ref={setNodeRef}
      className={`tray-item${isDragging ? ' dragging' : ''}`}
      {...attributes}
      {...listeners}
      aria-label={`Arrastar ${name}`}
    >
      <span className={`code-badge${id === 'rest' ? ' muted' : ''}`}>{code}</span>
      {name}
    </div>
  )
}

function DayCard({ day, today, closed, justSet, children }: { day: number; today: boolean; closed: boolean; justSet: boolean; children: React.ReactNode }) {
  const { isOver, setNodeRef } = useDroppable({ id: day, disabled: closed })
  return (
    <section
      ref={setNodeRef}
      className={`day${isOver ? ' over' : ''}${today ? ' today' : ''}${closed ? ' closed' : ''}${justSet ? ' just-set' : ''}`}
      aria-label={DAYS[day - 1]}
    >
      {children}
    </section>
  )
}
