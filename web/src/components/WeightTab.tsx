import { kg, parseDay, shortDate } from '../lib/format'
import type { WeightPoint } from '../sync/types'
import { LineChart } from './LineChart'

/** Pesagens que a atleta escolheu compartilhar (Ajustes › Painel do treinador no app). */
export function WeightTab({ points }: { points: WeightPoint[] | null | undefined }) {
  if (points === undefined) return <p className="empty">Carregando…</p>
  if (points === null) {
    return <p className="card empty">A atleta não compartilha o peso. Ela pode ligar em Ajustes › Painel do treinador, no app.</p>
  }
  if (points.length === 0) return <p className="card empty">Nenhuma pesagem registrada ainda.</p>

  const sorted = [...points].sort((a, b) => a.date.localeCompare(b.date))
  const first = sorted[0]
  const last = sorted[sorted.length - 1]
  const diff = last.kg - first.kg

  return (
    <div className="stack" style={{ gap: 18 }}>
      <div className="stats">
        <div className="stat">
          <b>{kg(last.kg)} kg</b>
          <span>última pesagem, {shortDate(parseDay(last.date))}</span>
        </div>
        <div className="stat">
          <b>
            {diff > 0 ? '+' : ''}
            {kg(Math.round(diff * 10) / 10)} kg
          </b>
          <span>desde {shortDate(parseDay(first.date))}</span>
        </div>
        <div className="stat">
          <b>{points.length}</b>
          <span>pesagens</span>
        </div>
      </div>
      <section className="card">
        <LineChart ariaLabel="Peso ao longo do tempo" unit="kg" points={sorted.map((p) => ({ label: shortDate(parseDay(p.date)), value: p.kg }))} />
      </section>
    </div>
  )
}
