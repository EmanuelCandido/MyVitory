import { useMemo, useState } from 'react'
import { equipments, muscleLabel, muscles, normalize, similarTo, type Exercise } from '../lib/catalog'
import { Modal, Thumb } from './ui'

/**
 * Escolha de exercício: busca, filtro por músculo e equipamento. Com [similarTo], abre nos parecidos
 * (mesma pontuação das alternativas do app) para trocar um exercício por outro.
 */
export function ExercisePicker({ title, pool, exclude = [], similar, onPick, onClose }: {
  title: string
  pool: Exercise[]
  exclude?: string[]
  similar?: Exercise
  onPick: (exercise: Exercise) => void
  onClose: () => void
}) {
  const [search, setSearch] = useState('')
  const [muscle, setMuscle] = useState<string | null>(null)
  const [equipment, setEquipment] = useState('')
  const [onlySimilar, setOnlySimilar] = useState(Boolean(similar))

  const list = useMemo(() => {
    const base = similar && onlySimilar ? similarTo(similar, pool) : pool
    const words = normalize(search).split(/\s+/).filter(Boolean)
    return base.filter(
      (e) =>
        words.every((w) => normalize(e.name).includes(w)) &&
        (!muscle || e.primary.includes(muscle) || e.secondary.includes(muscle)) &&
        (!equipment || e.equipment === equipment),
    )
  }, [pool, similar, onlySimilar, search, muscle, equipment])

  const header = (
    <>
      <div className="row wrap">
        <input className="input" style={{ flex: 1, minWidth: 200 }} placeholder="Buscar pelo nome" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Buscar exercício" />
        <select className="select" style={{ width: 180 }} value={equipment} onChange={(e) => setEquipment(e.target.value)} aria-label="Equipamento">
          <option value="">Todo equipamento</option>
          {equipments.map((e) => (
            <option key={e}>{e}</option>
          ))}
        </select>
      </div>
      <div className="row wrap" style={{ gap: 6 }}>
        {similar && (
          <button type="button" className="filter-chip" aria-pressed={onlySimilar} onClick={() => setOnlySimilar(!onlySimilar)}>
            Parecidos com {similar.name}
          </button>
        )}
        {muscles.map((m) => (
          <button key={m.id} type="button" className="filter-chip" aria-pressed={muscle === m.id} onClick={() => setMuscle(muscle === m.id ? null : m.id)}>
            {m.label}
          </button>
        ))}
      </div>
    </>
  )

  return (
    <Modal title={title} onClose={onClose} header={header}>
      {list.length === 0 ? (
        <p className="empty">Nenhum exercício com esses filtros.</p>
      ) : (
        <div className="exercise-grid">
          {list.map((e) => {
            const disabled = exclude.includes(e.id)
            return (
              <button key={e.id} type="button" className="exercise-card" aria-disabled={disabled} disabled={disabled} onClick={() => onPick(e)}>
                <Thumb exercise={e} />
                <span>
                  <strong>{e.name}</strong>
                  <span className="small muted">
                    {e.primary.map(muscleLabel).join(', ')}
                    {e.equipment && ` · ${e.equipment}`}
                    {disabled && ' · já está no treino'}
                    {e.custom && ' · próprio'}
                  </span>
                </span>
              </button>
            )
          })}
        </div>
      )}
    </Modal>
  )
}
