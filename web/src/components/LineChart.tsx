import { useState } from 'react'

export interface Point {
  label: string
  value: number
  detail?: string
}

/** Gráfico de linha simples em SVG (carga ou peso ao longo do tempo), com o último ponto destacado. */
export function LineChart({ points, unit, height = 220, ariaLabel }: { points: Point[]; unit: string; height?: number; ariaLabel: string }) {
  const [hover, setHover] = useState<number | null>(null)
  if (points.length === 0) return null
  const width = 720
  const pad = { top: 18, right: 18, bottom: 28, left: 44 }
  const values = points.map((p) => p.value)
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (min === max) {
    min -= 1
    max += 1
  }
  const span = max - min
  min -= span * 0.1
  max += span * 0.1
  const x = (i: number) => pad.left + (points.length === 1 ? (width - pad.left - pad.right) / 2 : (i * (width - pad.left - pad.right)) / (points.length - 1))
  const y = (v: number) => pad.top + ((max - v) * (height - pad.top - pad.bottom)) / (max - min)
  const ticks = [min + (max - min) * 0.1, (min + max) / 2, max - (max - min) * 0.1]
  const fmt = (v: number) => (Math.abs(v) >= 100 ? v.toFixed(0) : v.toFixed(1)).replace('.', ',')
  const active = hover ?? points.length - 1
  const step = Math.max(1, Math.ceil(points.length / 8))

  return (
    <figure style={{ margin: 0 }}>
      <svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={ariaLabel} onMouseLeave={() => setHover(null)}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} stroke="var(--line)" />
            <text x={pad.left - 8} y={y(t) + 4} textAnchor="end">
              {fmt(t)}
            </text>
          </g>
        ))}
        <polyline fill="none" stroke="var(--lime)" strokeWidth={2.5} strokeLinejoin="round" points={points.map((p, i) => `${x(i)},${y(p.value)}`).join(' ')} />
        {points.map((p, i) => (
          <g key={`${p.label}-${i}`} onMouseEnter={() => setHover(i)}>
            <rect x={x(i) - 12} y={pad.top} width={24} height={height - pad.top - pad.bottom} fill="transparent" />
            <circle cx={x(i)} cy={y(p.value)} r={i === active ? 5.5 : 3} fill={i === active ? 'var(--lime)' : 'var(--surface-high)'} stroke="var(--lime)" strokeWidth={1.5} />
            {(i % step === 0 || i === points.length - 1) && (
              <text x={x(i)} y={height - 8} textAnchor="middle">
                {p.label}
              </text>
            )}
          </g>
        ))}
      </svg>
      <figcaption className="small muted" aria-live="polite">
        {points[active].label}: <strong style={{ color: 'var(--text)' }}>{fmt(points[active].value)} {unit}</strong>
        {points[active].detail && ` · ${points[active].detail}`}
      </figcaption>
    </figure>
  )
}
