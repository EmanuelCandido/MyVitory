import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { Exercise } from '../lib/catalog'

export type Tone = 'flame' | 'volt' | 'paper' | 'sky' | 'dim'

/** Etiqueta chanfrada: número grande e unidade pequena ("05 EXERC"). Só mostra dado, nunca recebe clique. */
export function Ticket({ value, unit, tone = 'flame', small, title }: { value: ReactNode; unit?: string; tone?: Tone; small?: boolean; title?: string }) {
  return (
    <span className={`ticket${tone === 'flame' ? '' : ` ${tone}`}${small ? ' sm' : ''}`} title={title}>
      {value}
      {unit && <small>{unit}</small>}
    </span>
  )
}

/** Selo de alerta (triângulo laranja). Decorativo: o texto ao lado diz o que é. */
export function WarnBadge({ size = 18 }: { size?: number }) {
  return (
    <svg className="badge" width={size} height={size} viewBox="0 0 40 40" aria-hidden="true">
      <path d="M17.4 5.5a3 3 0 0 1 5.2 0l14 24.3a3 3 0 0 1-2.6 4.5H6a3 3 0 0 1-2.6-4.5z" fill="var(--flame)" stroke="var(--ink)" strokeWidth="2.5" strokeLinejoin="round" />
      <path d="M20 13v10" stroke="var(--ink)" strokeWidth="4" strokeLinecap="round" />
      <circle cx="20" cy="28.5" r="2.4" fill="var(--ink)" />
    </svg>
  )
}

/** Aviso com selo: "⚠ Quadríceps: 24 h desde o treino anterior". */
export function Warn({ children }: { children: ReactNode }) {
  return (
    <span className="warn">
      <WarnBadge />
      <span>{children}</span>
    </span>
  )
}

export function Stepper({ label, value, display, onChange, step = 1, min, max }: {
  label: string
  value: number
  display?: string
  onChange: (value: number) => void
  step?: number
  min: number
  max: number
}) {
  return (
    <div>
      <span className="stepper-label">{label}</span>
      <div className="stepper" role="group" aria-label={label}>
        <button type="button" aria-label={`Diminuir ${label.toLowerCase()}`} disabled={value <= min} onClick={() => onChange(Math.max(min, value - step))}>
          −
        </button>
        <output aria-live="polite">{display ?? value}</output>
        <button type="button" aria-label={`Aumentar ${label.toLowerCase()}`} disabled={value >= max} onClick={() => onChange(Math.min(max, value + step))}>
          +
        </button>
      </div>
    </div>
  )
}

export function Thumb({ exercise, size = 52 }: { exercise: Exercise; size?: number }) {
  const [failed, setFailed] = useState(false)
  if (!exercise.gif || failed) {
    return (
      <div className="thumb placeholder" style={{ width: size, height: size }} aria-hidden>
        {exercise.name.slice(0, 1)}
      </div>
    )
  }
  return <img className="thumb" src={exercise.gif} alt="" loading="lazy" width={size} height={size} style={{ width: size, height: size }} onError={() => setFailed(true)} />
}

export function Modal({ title, onClose, children, header, narrow }: { title: string; onClose: () => void; children: ReactNode; header?: ReactNode; narrow?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    ref.current?.querySelector<HTMLElement>('input, button, select, textarea')?.focus()
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      previous?.focus()
    }
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal${narrow ? ' narrow' : ''}`} role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <div className="modal-head">
          <div className="row between">
            <h2 style={{ margin: 0 }}>{title}</h2>
            <button className="btn ghost icon" type="button" aria-label="Fechar" onClick={onClose}>
              ✕
            </button>
          </div>
          {header}
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}

export function Confirm({ title, body, action, onConfirm, onClose }: { title: string; body: ReactNode; action: string; onConfirm: () => void; onClose: () => void }) {
  return (
    <Modal title={title} onClose={onClose} narrow>
      <div className="stack">
        <div className="muted">{body}</div>
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button className="btn ghost" type="button" onClick={onClose}>
            Cancelar
          </button>
          <button
            className="btn danger"
            type="button"
            onClick={() => {
              onConfirm()
              onClose()
            }}
          >
            {action}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/** Campo de texto que salva ao sair (ou com Enter), como o nome do treino no app. */
export function CommitInput({ value, onCommit, multiline, ...rest }: {
  value: string
  onCommit: (value: string) => void
  multiline?: boolean
  placeholder?: string
  className?: string
  maxLength?: number
  'aria-label'?: string
}) {
  const [text, setText] = useState(value)
  useEffect(() => setText(value), [value])
  const commit = () => text !== value && onCommit(text)
  if (multiline) {
    return <textarea {...rest} rows={1} value={text} onChange={(e) => setText(e.target.value)} onBlur={commit} />
  }
  return (
    <input
      {...rest}
      value={text}
      onChange={(e) => setText(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') (e.target as HTMLInputElement).blur()
        if (e.key === 'Escape') setText(value)
      }}
    />
  )
}

/**
 * Botão que abre uma lista de opções com o nome inteiro (o select nativo cortava o nome do treino nos cards).
 * Fecha ao escolher, com Esc ou clicando fora.
 */
export function Menu({ label, ariaLabel, children }: { label: ReactNode; ariaLabel: string; children: (close: () => void) => ReactNode }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: PointerEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return (
    <div className="menu" ref={ref}>
      <button type="button" className="btn small menu-button" aria-haspopup="menu" aria-expanded={open} aria-label={ariaLabel} onClick={() => setOpen((o) => !o)}>
        {label}
        <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="menu-list" role="menu">
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  )
}

export function MenuItem({ selected, onSelect, children }: { selected?: boolean; onSelect: () => void; children: ReactNode }) {
  return (
    <button type="button" role="menuitemradio" aria-checked={selected ?? false} className="menu-item" onClick={onSelect}>
      {children}
    </button>
  )
}
