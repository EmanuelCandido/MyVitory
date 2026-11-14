import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { Exercise } from '../lib/catalog'
import { currentMotion, saveData } from '../lib/device'

export type Tone = 'flame' | 'volt' | 'paper' | 'sky' | 'dim'

/**
 * Etiqueta chanfrada: número grande e unidade pequena ("05 EXERC"). Só mostra dado, nunca recebe clique.
 * Com [stamp], entra como adesivo batido; [stamp] é a posição na faixa (70 ms entre cada).
 */
export function Ticket({ value, unit, tone = 'flame', small, title, stamp }: { value: ReactNode; unit?: string; tone?: Tone; small?: boolean; title?: string; stamp?: number }) {
  return (
    <span
      className={`ticket${tone === 'flame' ? '' : ` ${tone}`}${small ? ' sm' : ''}${stamp !== undefined ? ' stamp-in' : ''}`}
      style={stamp !== undefined ? ({ '--i': stamp } as CSSProperties) : undefined}
      title={title}
    >
      {value}
      {unit && <small>{unit}</small>}
    </span>
  )
}

/**
 * Número que conta até o valor (500 ms, desacelerando), só no movimento completo. Ao mudar, conta do valor
 * anterior até o novo. No leve e no desligado, devolve o valor direto.
 */
export function useCountUp(target: number, duration = 500): number {
  const [value, setValue] = useState(() => (currentMotion() === 'full' ? 0 : target))
  const from = useRef(currentMotion() === 'full' ? 0 : target)
  useEffect(() => {
    // Aba em segundo plano: o navegador pausa requestAnimationFrame e o número ficaria parado no início.
    if (currentMotion() !== 'full' || document.hidden || from.current === target) {
      from.current = target
      setValue(target)
      return
    }
    const start = performance.now()
    const begin = from.current
    let frame = 0
    const step = (now: number) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      setValue(Math.round(begin + (target - begin) * eased))
      if (t < 1) frame = requestAnimationFrame(step)
      else from.current = target
    }
    frame = requestAnimationFrame(step)
    return () => {
      cancelAnimationFrame(frame)
      from.current = target
    }
  }, [target, duration])
  return value
}

/**
 * Abas em pílula com indicador volt que desliza até a escolhida. Antes da primeira medida (ou sem JS de layout),
 * a aba ativa já tem o fundo volt: nada pisca.
 */
export function TabBar<T extends string>({ tabs, value, onChange, label }: { tabs: readonly T[]; value: T; onChange: (tab: T) => void; label: string }) {
  const list = useRef<HTMLElement>(null)
  const [box, setBox] = useState<{ x: number; w: number } | null>(null)
  useLayoutEffect(() => {
    const el = list.current
    if (!el) return
    const measure = () => {
      const active = el.querySelector<HTMLElement>('[aria-selected="true"]')
      if (active) setBox({ x: active.offsetLeft, w: active.offsetWidth })
    }
    measure()
    // A fonte mono chega depois do primeiro desenho e muda a largura das abas.
    void document.fonts?.ready.then(measure)
    const observer = new ResizeObserver(measure)
    observer.observe(el)
    return () => observer.disconnect()
  }, [value])
  return (
    <nav className={`tabs${box ? ' ready' : ''}`} role="tablist" aria-label={label} ref={list}>
      {box && <span className="tabs-indicator" aria-hidden="true" style={{ transform: `translateX(${box.x}px)`, width: box.w }} />}
      {tabs.map((t) => (
        <button
          key={t}
          type="button"
          role="tab"
          aria-selected={value === t}
          onClick={(e) => {
            onChange(t)
            // No celular as abas rolam de lado: a escolhida fica à vista.
            e.currentTarget.scrollIntoView({ block: 'nearest', inline: 'nearest' })
          }}
        >
          {t}
        </button>
      ))}
    </nav>
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

/** Aviso com selo: "⚠ Quadríceps: 24 h desde o treino anterior". O selo balança uma vez ao aparecer. */
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
  // Direção da última mudança: + entra de baixo, − entra de cima.
  const previous = useRef(value)
  const direction = value > previous.current ? 'up' : value < previous.current ? 'down' : ''
  useEffect(() => {
    previous.current = value
  }, [value])
  return (
    <div>
      <span className="stepper-label">{label}</span>
      <div className="stepper" role="group" aria-label={label}>
        <button type="button" aria-label={`Diminuir ${label.toLowerCase()}`} disabled={value <= min} onClick={() => onChange(Math.max(min, value - step))}>
          −
        </button>
        <output aria-live="polite">
          <span key={value} className={direction}>
            {display ?? value}
          </span>
        </output>
        <button type="button" aria-label={`Aumentar ${label.toLowerCase()}`} disabled={value >= max} onClick={() => onChange(Math.min(max, value + step))}>
          +
        </button>
      </div>
    </div>
  )
}

/** Um observador para todas as miniaturas (82 no catálogo): carrega só o que chega perto da tela. */
let thumbObserver: IntersectionObserver | null = null
const thumbCallbacks = new WeakMap<Element, () => void>()
function observeThumb(el: Element, onVisible: () => void): () => void {
  thumbObserver ??= new IntersectionObserver(
    (entries) =>
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return
        thumbObserver?.unobserve(entry.target)
        thumbCallbacks.get(entry.target)?.()
        thumbCallbacks.delete(entry.target)
      }),
    { rootMargin: '200px' },
  )
  thumbCallbacks.set(el, onVisible)
  thumbObserver.observe(el)
  return () => {
    thumbObserver?.unobserve(el)
    thumbCallbacks.delete(el)
  }
}

/**
 * Miniatura parada: só o primeiro quadro do GIF, desenhado num canvas quando o card entra na tela.
 * Dezenas de GIFs animados decodificando ao mesmo tempo travam um PC fraco; a animação fica no detalhe do
 * exercício (como no app). Com economia de dados, nem baixa: mostra a inicial.
 */
export function Thumb({ exercise, size = 52 }: { exercise: Exercise; size?: number }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const [failed, setFailed] = useState(() => !exercise.gif || saveData())
  // Mesmo componente, outro exercício (troca no treino): tenta de novo.
  useEffect(() => setFailed(!exercise.gif || saveData()), [exercise.gif])
  useEffect(() => {
    const el = canvas.current
    const src = exercise.gif
    if (!el || !src || failed) return
    let image: HTMLImageElement | null = null
    const stop = observeThumb(el, () => {
      image = new Image()
      image.decoding = 'async'
      image.onload = () => {
        const img = image
        const ctx = el.getContext('2d')
        if (!img || !ctx) return
        // object-fit: cover, recortando o centro.
        const side = Math.min(img.naturalWidth, img.naturalHeight)
        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, el.width, el.height)
        ctx.drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, el.width, el.height)
        image = null
      }
      image.onerror = () => setFailed(true)
      image.src = src
    })
    return () => {
      stop()
      if (image) {
        image.onload = null
        image.onerror = null
        image.src = ''
      }
    }
  }, [exercise.gif, failed])
  if (failed) {
    return (
      <div className="thumb placeholder" style={{ width: size, height: size }} aria-hidden>
        {exercise.name.slice(0, 1)}
      </div>
    )
  }
  const scale = Math.min(2, Math.ceil(window.devicePixelRatio || 1))
  return <canvas ref={canvas} className="thumb" width={size * scale} height={size * scale} style={{ width: size, height: size }} aria-hidden="true" />
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
