import { useMemo, useState } from 'react'
import { allExercises, equipments, muscleLabel, muscles, normalize, type Exercise } from '../lib/catalog'
import { deleteExercise, newId, saveExercise, templatesUsing } from '../sync/edits'
import type { PlanSnapshot } from '../sync/types'
import { Confirm, Modal, Thumb } from './ui'

type Edit = (change: (p: PlanSnapshot) => PlanSnapshot) => void

interface Draft {
  id: string | null
  name: string
  equipment: string
  primary: string[]
  secondary: string[]
}

/** Catálogo para consulta (GIF, músculos, pontos-chave) e os exercícios próprios da atleta. */
export function ExercisesTab({ plan, edit }: { plan: PlanSnapshot; edit: Edit }) {
  const pool = useMemo(() => allExercises(plan), [plan])
  const [search, setSearch] = useState('')
  const [muscle, setMuscle] = useState<string | null>(null)
  const [open, setOpen] = useState<Exercise | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [deleting, setDeleting] = useState<Exercise | null>(null)

  const words = normalize(search).split(/\s+/).filter(Boolean)
  const list = pool.filter((e) => words.every((w) => normalize(e.name).includes(w)) && (!muscle || e.primary.includes(muscle) || e.secondary.includes(muscle)))
  const custom = pool.filter((e) => e.custom)

  return (
    <div className="stack" style={{ gap: 20 }}>
      <section>
        <div className="row between wrap" style={{ marginBottom: 10 }}>
          <p className="section-label" style={{ margin: 0 }}>
            Exercícios próprios · {custom.length}
          </p>
          <button className="btn primary small" type="button" onClick={() => setDraft({ id: null, name: '', equipment: 'Máquina', primary: [], secondary: [] })}>
            + Criar exercício
          </button>
        </div>
        {custom.length === 0 ? (
          <p className="small muted">Nenhum ainda. Exercícios próprios aparecem no app da atleta e contam para a recuperação e o recálculo.</p>
        ) : (
          <div className="exercise-grid">
            {custom.map((e) => (
              <div key={e.id} className="exercise-card" style={{ cursor: 'default' }}>
                <Thumb exercise={e} />
                <span style={{ flex: 1 }}>
                  <strong>{e.name}</strong>
                  <span className="small muted">
                    {e.primary.map(muscleLabel).join(', ')} · {e.equipment}
                  </span>
                </span>
                <span className="stack" style={{ gap: 4 }}>
                  <button className="btn small" type="button" onClick={() => setDraft({ id: e.id, name: e.name, equipment: e.equipment, primary: e.primary, secondary: e.secondary })}>
                    Editar
                  </button>
                  <button className="btn small ghost" type="button" onClick={() => setDeleting(e)}>
                    Apagar
                  </button>
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <p className="section-label">Catálogo · {list.length}</p>
        <div className="stack" style={{ marginBottom: 12 }}>
          <input className="input" placeholder="Buscar pelo nome" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Buscar no catálogo" />
          <div className="row wrap" style={{ gap: 6 }}>
            {muscles.map((m) => (
              <button key={m.id} type="button" className="filter-chip" aria-pressed={muscle === m.id} onClick={() => setMuscle(muscle === m.id ? null : m.id)}>
                {m.label}
              </button>
            ))}
          </div>
        </div>
        <div className="exercise-grid">
          {list.map((e) => (
            <button key={e.id} type="button" className="exercise-card" onClick={() => setOpen(e)}>
              <Thumb exercise={e} />
              <span>
                <strong>{e.name}</strong>
                <span className="small muted">
                  {e.primary.map(muscleLabel).join(', ')}
                  {e.equipment && ` · ${e.equipment}`}
                </span>
              </span>
            </button>
          ))}
        </div>
      </section>

      {open && (
        <Modal title={open.name} onClose={() => setOpen(null)}>
          <div className="detail">
            {open.gif ? <img src={open.gif} alt={`Movimento: ${open.name}`} /> : <div className="thumb placeholder" style={{ width: '100%', height: 200 }} />}
            <div className="stack">
              <span className="muted">
                {open.equipment} · principal: {open.primary.map(muscleLabel).join(', ') || '—'}
                {open.secondary.length > 0 && ` · também: ${open.secondary.map(muscleLabel).join(', ')}`}
              </span>
              {open.tips.length > 0 && (
                <div>
                  <p className="section-label" style={{ margin: '6px 0 0' }}>
                    Pontos-chave
                  </p>
                  <ul>
                    {open.tips.map((t) => (
                      <li key={t}>{t}</li>
                    ))}
                  </ul>
                </div>
              )}
              {open.videoId && (
                <a href={`https://www.youtube.com/watch?v=${open.videoId}`} target="_blank" rel="noreferrer">
                  Ver o vídeo de execução no YouTube
                </a>
              )}
              <span className="small faint">Usado em: {templatesUsing(plan, open.id).map((t) => t.name).join(', ') || 'nenhum treino'}</span>
            </div>
          </div>
        </Modal>
      )}

      {draft && (
        <ExerciseForm
          draft={draft}
          onClose={() => setDraft(null)}
          onSave={(d) => {
            edit((p) => saveExercise(p, d.id ?? `custom_${newId().slice(0, 16)}`, { name: d.name, equipment: d.equipment, primary: d.primary, secondary: d.secondary }))
            setDraft(null)
          }}
        />
      )}

      {deleting &&
        (templatesUsing(plan, deleting.id).length > 0 ? (
          <Modal title="Exercício em uso" onClose={() => setDeleting(null)} narrow>
            <p className="muted">
              {deleting.name} está em {templatesUsing(plan, deleting.id).map((t) => t.name).join(', ')}. Tire dos treinos antes de apagar.
            </p>
          </Modal>
        ) : (
          <Confirm
            title={`Apagar ${deleting.name}?`}
            body="Some do app da atleta também. Se ela já registrou séries com ele, o app mantém o exercício para não perder o histórico."
            action="Apagar"
            onConfirm={() => edit((p) => deleteExercise(p, deleting.id))}
            onClose={() => setDeleting(null)}
          />
        ))}
    </div>
  )
}

function ExerciseForm({ draft, onSave, onClose }: { draft: Draft; onSave: (d: Draft) => void; onClose: () => void }) {
  const [d, setD] = useState(draft)
  const toggle = (list: 'primary' | 'secondary', id: string) =>
    setD((cur) => {
      const has = cur[list].includes(id)
      const other = list === 'primary' ? 'secondary' : 'primary'
      return { ...cur, [list]: has ? cur[list].filter((m) => m !== id) : [...cur[list], id], [other]: cur[other].filter((m) => m !== id) }
    })
  const valid = d.name.trim().length > 0 && d.primary.length > 0

  return (
    <Modal title={draft.id ? 'Editar exercício' : 'Novo exercício'} onClose={onClose} narrow>
      <form
        className="stack"
        onSubmit={(e) => {
          e.preventDefault()
          if (valid) onSave(d)
        }}
      >
        <label className="field">
          <span>Nome</span>
          <input className="input" value={d.name} maxLength={60} onChange={(e) => setD({ ...d, name: e.target.value })} required />
        </label>
        <label className="field">
          <span>Equipamento</span>
          <select className="select" value={d.equipment} onChange={(e) => setD({ ...d, equipment: e.target.value })}>
            {[...new Set([...equipments, d.equipment])].map((e) => (
              <option key={e}>{e}</option>
            ))}
          </select>
        </label>
        <div className="field">
          <span>Músculo principal (conta inteiro na recuperação)</span>
          <div className="row wrap" style={{ gap: 6 }}>
            {muscles.map((m) => (
              <button key={m.id} type="button" className="filter-chip" aria-pressed={d.primary.includes(m.id)} onClick={() => toggle('primary', m.id)}>
                {m.label}
              </button>
            ))}
          </div>
        </div>
        <div className="field">
          <span>Também trabalha (conta metade)</span>
          <div className="row wrap" style={{ gap: 6 }}>
            {muscles.map((m) => (
              <button key={m.id} type="button" className="filter-chip" aria-pressed={d.secondary.includes(m.id)} onClick={() => toggle('secondary', m.id)}>
                {m.label}
              </button>
            ))}
          </div>
        </div>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button className="btn ghost" type="button" onClick={onClose}>
            Cancelar
          </button>
          <button className="btn primary" type="submit" disabled={!valid}>
            Salvar
          </button>
        </div>
      </form>
    </Modal>
  )
}
