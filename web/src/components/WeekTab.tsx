import { useState } from 'react'
import { DndContext, DragOverlay, KeyboardSensor, PointerSensor, useDraggable, useDroppable, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { estimateMinutes, primaryMuscles, recoveryWarnings, warningText } from '../lib/analysis'
import { muscleLabel } from '../lib/catalog'
import { addDays, DAYS, dayOfWeek, isoDay, plural, shortDate, weekStart } from '../lib/format'
import { setClosedWeekday, setDay } from '../sync/edits'
import type { DayDoc, PlanSnapshot } from '../sync/types'

const STATUS: Record<DayDoc['status'], { label: string; tone: string }> = {
  DONE: { label: 'Feito', tone: 'lime' },
  PARTIAL: { label: 'Parcial', tone: 'blue' },
  MISSED: { label: 'Não feito', tone: 'orange' },
  PLANNED: { label: 'Planejado', tone: '' },
  REST: { label: 'Descanso', tone: '' },
}

export function statusChip(day: DayDoc, today: string) {
  if (day.status === 'MISSED' && day.movedTo) return <span className="chip blue">Movido</span>
  if (day.closed && day.status !== 'DONE' && day.status !== 'PARTIAL') return <span className="chip">{day.closed}</span>
  if (day.unavailable && day.status !== 'DONE' && day.status !== 'PARTIAL') return <span className="chip orange">Indisponível</span>
  if (day.status === 'PLANNED' && day.date < today) return <span className="chip orange">Sem registro</span>
  if (day.status === 'PLANNED' && day.date === today) return <span className="chip lime">Hoje</span>
  const s = STATUS[day.status]
  return <span className={`chip ${s.tone}`}>{s.label}</span>
}

/** Plano base da semana: arraste um treino para um dia ou escolha na lista. */
export function WeekTab({ plan, days, edit }: { plan: PlanSnapshot; days: DayDoc[] | undefined; edit: (change: (p: PlanSnapshot) => PlanSnapshot) => void }) {
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }), useSensor(KeyboardSensor))
  const [dragging, setDragging] = useState<string | null>(null)
  const templates = Object.entries(plan.templates).sort(([, a], [, b]) => a.code.localeCompare(b.code))
  const warnings = recoveryWarnings(plan)

  const now = new Date()
  const today = isoDay(now)
  const start = weekStart(now)
  const thisWeek = new Map((days ?? []).map((d) => [d.date, d]))

  const onDragEnd = (e: DragEndEvent) => {
    setDragging(null)
    const day = e.over?.id
    if (typeof day !== 'number') return
    const templateId = String(e.active.id) === 'rest' ? null : String(e.active.id)
    // Dia com a academia fechada não recebe treino: primeiro desmarque o "fechada".
    if (templateId !== null && plan.week[String(day)]?.closed) return
    edit((p) => setDay(p, day, templateId))
  }

  return (
    <DndContext sensors={sensors} onDragStart={(e) => setDragging(String(e.active.id))} onDragCancel={() => setDragging(null)} onDragEnd={onDragEnd}>
      <p className="section-label">Treinos · arraste para um dia</p>
      <div className="tray">
        {templates.map(([id, t]) => (
          <TrayItem key={id} id={id} code={t.code} name={t.name} />
        ))}
        <TrayItem id="rest" code="–" name="Descanso" />
      </div>

      <p className="section-label">Plano base da semana</p>
      <div className="week">
        {DAYS.map((name, index) => {
          const day = index + 1
          const templateId = plan.week[String(day)]?.templateId ?? null
          const template = templateId ? plan.templates[templateId] : undefined
          const closed = plan.week[String(day)]?.closed === true
          const date = addDays(start, index)
          const real = thisWeek.get(isoDay(date))
          const dayWarnings = warnings.filter((w) => w.day === day)
          return (
            <DayCard key={day} day={day} today={dayOfWeek(now) === day}>
              <div className="row between">
                <span className="day-name">{name}</span>
                <span className="small faint">{shortDate(date)}</span>
              </div>
              {closed ? (
                <span className="day-rest">Academia fechada</span>
              ) : template ? (
                <div className="day-template">
                  <div className="row">
                    <span className="code-badge">{template.code}</span>
                    <strong>{template.name}</strong>
                  </div>
                  <span className="small muted">
                    {plural(template.items.length, 'exercício', 'exercícios')} · ~{estimateMinutes(template)} min
                  </span>
                  <span className="small faint">{primaryMuscles(plan, template).map(muscleLabel).join(', ')}</span>
                </div>
              ) : (
                <span className="day-rest">Descanso</span>
              )}
              {dayWarnings.map((w) => (
                <span key={w.muscle} className="small" style={{ color: 'var(--orange)' }}>
                  ⚠ {warningText(w)}
                </span>
              ))}
              <select
                className="select"
                aria-label={`Treino de ${name.toLowerCase()}`}
                value={templateId ?? ''}
                disabled={closed}
                onChange={(e) => edit((p) => setDay(p, day, e.target.value || null))}
              >
                <option value="">Descanso</option>
                {templates.map(([id, t]) => (
                  <option key={id} value={id}>
                    {t.code} · {t.name}
                  </option>
                ))}
              </select>
              <label className="row small muted" style={{ gap: 6, cursor: 'pointer' }}>
                <input type="checkbox" checked={closed} onChange={(e) => edit((p) => setClosedWeekday(p, day, e.target.checked))} />
                Academia fechada
              </label>
              {real && (
                <div className="real">
                  <span className="faint">No celular esta semana</span>
                  <span className="row wrap" style={{ gap: 6 }}>
                    {statusChip(real, today)}
                    <span>{real.status === 'REST' ? 'Descanso' : real.name}</span>
                  </span>
                  {real.special && <span className="faint">Mudado só nesta semana</span>}
                  {real.comment && <span className="muted">“{real.comment}”</span>}
                </div>
              )}
            </DayCard>
          )
        })}
      </div>
      <p className="small faint" style={{ marginTop: 14 }}>
        Mudanças no plano base valem para os próximos dias que ainda não começaram. Dias mudados só nesta semana no celular (ou pelo
        recálculo) continuam como estão. Dia com a academia fechada vira descanso e o recálculo nunca põe treino nele; feriados e
        treinos de uma data específica ficam na aba Calendário.
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
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id })
  return (
    <div ref={setNodeRef} className={`tray-item${isDragging ? ' dragging' : ''}`} {...attributes} {...listeners} aria-label={`Arrastar ${name}`}>
      <span className={`code-badge${id === 'rest' ? ' muted' : ''}`}>{code}</span>
      {name}
    </div>
  )
}

function DayCard({ day, today, children }: { day: number; today: boolean; children: React.ReactNode }) {
  const { isOver, setNodeRef } = useDroppable({ id: day })
  return (
    <section ref={setNodeRef} className={`day${isOver ? ' over' : ''}${today ? ' today' : ''}`} aria-label={DAYS[day - 1]}>
      {children}
    </section>
  )
}
