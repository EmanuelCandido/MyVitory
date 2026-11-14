import { useEffect, useMemo, useRef, useState } from 'react'
import { closestCenter, DndContext, KeyboardSensor, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { estimateMinutes, primaryMuscles, totalSets } from '../lib/analysis'
import { allExercises, exerciseById, models, muscleLabel, type Exercise } from '../lib/catalog'
import { clock, DAYS, plural } from '../lib/format'
import { createTemplate, deleteTemplate, move, newItem, updateTemplate } from '../sync/edits'
import { LIMITS, type PlanSnapshot, type SnapshotItem, type SnapshotTemplate } from '../sync/types'
import { ExercisePicker } from './ExercisePicker'
import { CommitInput, Confirm, Modal, Stepper, Thumb, Ticket } from './ui'

type Edit = (change: (p: PlanSnapshot) => PlanSnapshot) => void

/** Lista de treinos à esquerda, editor do escolhido à direita. */
export function TemplatesTab({ plan, edit }: { plan: PlanSnapshot; edit: Edit }) {
  const entries = Object.entries(plan.templates).sort(([, a], [, b]) => a.code.localeCompare(b.code))
  const [selected, setSelected] = useState<string | null>(entries[0]?.[0] ?? null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [choosingModel, setChoosingModel] = useState(false)

  // Treino apagado (aqui ou no celular): passa para o primeiro da lista.
  useEffect(() => {
    if (!selected || !plan.templates[selected]) setSelected(entries[0]?.[0] ?? null)
  }, [plan, selected, entries])

  const create = (name: string, items: SnapshotItem[] = []) => {
    let createdId = ''
    edit((p) => {
      const [next, id] = createTemplate(p, name, items)
      createdId = id
      return next
    })
    if (createdId) setSelected(createdId)
  }

  const daysOf = (id: string) =>
    Object.entries(plan.week)
      .filter(([, d]) => d.templateId === id)
      .map(([day]) => DAYS[Number(day) - 1].slice(0, 3))

  const current = selected ? plan.templates[selected] : undefined

  return (
    <div className="grid-2">
      <aside className="stack">
        <div className="template-list">
          {entries.map(([id, t]) => (
            <button key={id} type="button" className="template-row" aria-current={id === selected} onClick={() => setSelected(id)}>
              <span className="code-badge">{t.code}</span>
              <span style={{ minWidth: 0 }}>
                <strong style={{ display: 'block' }}>{t.name}</strong>
                <span className="meta">
                  {plural(t.items.length, 'exercício', 'exercícios')}
                  {daysOf(id).length > 0 ? ` · ${daysOf(id).join(', ')}` : ' · fora da semana'}
                </span>
              </span>
            </button>
          ))}
        </div>
        <div className="row wrap">
          {/* Uma pílula volt por tela: com treinos, a principal é "Adicionar exercício" no editor. */}
          <button className={`btn${entries.length === 0 ? ' primary' : ''}`} type="button" onClick={() => create('')}>
            + Novo treino
          </button>
          <button className="btn" type="button" onClick={() => setChoosingModel(true)}>
            Usar modelo
          </button>
        </div>
      </aside>

      {current && selected ? (
        <TemplateEditor
          key={selected}
          id={selected}
          template={current}
          plan={plan}
          edit={edit}
          onDuplicate={() => create(`${current.name} (cópia)`, current.items)}
          onDelete={() => setConfirmDelete(true)}
        />
      ) : (
        <div className="card empty">Nenhum treino ainda. Crie um ou comece de um modelo.</div>
      )}

      {confirmDelete && current && selected && (
        <Confirm
          title={`Apagar ${current.name}?`}
          body="Os dias do plano com este treino viram descanso e ele sai dos próximos dias no celular. Os treinos já feitos continuam no histórico."
          action="Apagar treino"
          onConfirm={() => edit((p) => deleteTemplate(p, selected))}
          onClose={() => setConfirmDelete(false)}
        />
      )}

      {choosingModel && (
        <Modal title="Começar de um modelo" onClose={() => setChoosingModel(false)} narrow>
          <div className="stack">
            <p className="muted" style={{ margin: 0 }}>
              Modelos da divisão de exemplo do app. O treino criado é uma cópia: dá para mudar à vontade.
            </p>
            {models.map((m) => (
              <button
                key={m.code}
                type="button"
                className="template-row"
                onClick={() => {
                  create(m.name, m.items.map((i) => ({ ...i, note: '' })))
                  setChoosingModel(false)
                }}
              >
                <span className="code-badge muted">{m.code}</span>
                <span>
                  <strong style={{ display: 'block' }}>{m.name}</strong>
                  <span className="meta">{m.items.map((i) => exerciseById(plan, i.exerciseId).name).join(', ')}</span>
                </span>
              </button>
            ))}
          </div>
        </Modal>
      )}
    </div>
  )
}

function TemplateEditor({ id, template, plan, edit, onDuplicate, onDelete }: {
  id: string
  template: SnapshotTemplate
  plan: PlanSnapshot
  edit: Edit
  onDuplicate: () => void
  onDelete: () => void
}) {
  const [adding, setAdding] = useState(false)
  const [swapping, setSwapping] = useState<number | null>(null)
  const pool = useMemo(() => allExercises(plan), [plan])
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }))

  // Chave estável pelo exercício (com o número da repetição, caso o mesmo apareça duas vezes).
  const keys = template.items.map((item, index) => `${item.exerciseId}#${template.items.slice(0, index).filter((i) => i.exerciseId === item.exerciseId).length}`)
  const ids = template.items.map((i) => i.exerciseId)
  // Exercício recém-adicionado (a lista cresceu): o último item sobe ao entrar.
  const count = useRef(template.items.length)
  const [fresh, setFresh] = useState<string | null>(null)
  useEffect(() => {
    if (template.items.length > count.current) setFresh(keys[keys.length - 1])
    count.current = template.items.length
  }, [template.items.length, keys])
  const change = (fn: (t: SnapshotTemplate) => SnapshotTemplate) => edit((p) => updateTemplate(p, id, fn))
  const setItem = (index: number, patch: Partial<SnapshotItem>) =>
    change((t) => ({ ...t, items: t.items.map((item, i) => (i === index ? { ...item, ...patch } : item)) }))

  const onDragEnd = (e: DragEndEvent) => {
    if (!e.over || e.active.id === e.over.id) return
    const from = keys.indexOf(String(e.active.id))
    const to = keys.indexOf(String(e.over.id))
    if (from >= 0 && to >= 0) change((t) => ({ ...t, items: move(t.items, from, to) }))
  }

  return (
    <section className="fade-in">
      <div className="editor-head">
        <span className="code-badge">{template.code}</span>
        <CommitInput
          className="input"
          aria-label="Nome do treino"
          value={template.name}
          maxLength={60}
          onCommit={(name) => name.trim() && change((t) => ({ ...t, name: name.trim() }))}
        />
        <button className="btn small" type="button" onClick={onDuplicate}>
          Duplicar
        </button>
        <button className="btn small danger" type="button" onClick={onDelete}>
          Apagar
        </button>
      </div>

      <div className="summary">
        <div className="strip tilt">
          <Ticket value={template.items.length} unit={template.items.length === 1 ? 'exercício' : 'exercícios'} tone="dim" />
          <Ticket value={totalSets(template)} unit="séries" tone="dim" />
          <Ticket value={`~${estimateMinutes(template)}`} unit="min" tone="dim" />
        </div>
        <span>{primaryMuscles(plan, template).map(muscleLabel).join(' · ')}</span>
      </div>

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
        <SortableContext items={keys} strategy={verticalListSortingStrategy}>
          <div className="items">
            {template.items.map((item, index) => (
              <ItemRow
                key={keys[index]}
                sortId={keys[index]}
                index={index}
                item={item}
                exercise={exerciseById(plan, item.exerciseId)}
                onChange={(patch) => setItem(index, patch)}
                onSwap={() => setSwapping(index)}
                fresh={keys[index] === fresh}
                onRemove={() => change((t) => ({ ...t, items: t.items.filter((_, i) => i !== index) }))}
              />
            ))}
          </div>
        </SortableContext>
      </DndContext>

      {template.items.length === 0 && <p className="card empty">Treino vazio. Adicione os exercícios.</p>}

      <button className="btn primary" type="button" style={{ marginTop: 14 }} onClick={() => setAdding(true)}>
        + Adicionar exercício
      </button>

      {adding && (
        <ExercisePicker
          title={`Adicionar ao ${template.name}`}
          pool={pool}
          exclude={ids}
          onClose={() => setAdding(false)}
          onPick={(e) => {
            change((t) => ({ ...t, items: [...t.items, newItem(e.id)] }))
            setAdding(false)
          }}
        />
      )}
      {swapping !== null && template.items[swapping] && (
        <ExercisePicker
          title={`Trocar ${exerciseById(plan, template.items[swapping].exerciseId).name}`}
          pool={pool}
          exclude={ids}
          similar={exerciseById(plan, template.items[swapping].exerciseId)}
          onClose={() => setSwapping(null)}
          onPick={(e: Exercise) => {
            setItem(swapping, { exerciseId: e.id })
            setSwapping(null)
          }}
        />
      )}
    </section>
  )
}

function ItemRow({ sortId, index, item, exercise, fresh, onChange, onSwap, onRemove }: {
  sortId: string
  fresh: boolean
  index: number
  item: SnapshotItem
  exercise: Exercise
  onChange: (patch: Partial<SnapshotItem>) => void
  onSwap: () => void
  onRemove: () => void
}) {
  const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id: sortId })
  return (
    <div ref={setNodeRef} className={`item${isDragging ? ' dragging' : ''}${fresh ? ' fresh' : ''}`} style={{ transform: CSS.Transform.toString(transform), transition }}>
      <div className="row" style={{ gap: 2 }}>
        <button ref={setActivatorNodeRef} className="handle" type="button" aria-label={`Mover ${exercise.name}`} {...attributes} {...listeners}>
          ⠿
        </button>
        <span className="index">{index + 1}</span>
      </div>
      <Thumb exercise={exercise} />
      <div className="item-main">
        <div className="item-title">
          <strong>{exercise.name}</strong>
          <span className="small muted">
            {exercise.primary.map(muscleLabel).join(', ')}
            {exercise.equipment && ` · ${exercise.equipment}`}
          </span>
        </div>
        <div className="item-controls">
          <Stepper label="Séries" value={item.sets} min={LIMITS.sets[0]} max={LIMITS.sets[1]} onChange={(sets) => onChange({ sets })} />
          <Stepper label="Reps" value={item.reps} min={LIMITS.reps[0]} max={LIMITS.reps[1]} onChange={(reps) => onChange({ reps })} />
          <Stepper
            label="Descanso"
            value={item.restSec}
            display={clock(item.restSec)}
            step={15}
            min={LIMITS.restSec[0]}
            max={LIMITS.restSec[1]}
            onChange={(restSec) => onChange({ restSec })}
          />
        </div>
        <CommitInput
          multiline
          className="textarea"
          aria-label={`Observação de ${exercise.name}`}
          placeholder="Observação para a atleta (ex.: descer em 3 s)"
          maxLength={LIMITS.text}
          value={item.note}
          onCommit={(note) => onChange({ note: note.trim() })}
        />
      </div>
      <div className="item-actions">
        <button className="btn small" type="button" onClick={onSwap}>
          Trocar
        </button>
        <button className="btn small ghost" type="button" onClick={onRemove} aria-label={`Remover ${exercise.name}`}>
          Remover
        </button>
      </div>
    </div>
  )
}
