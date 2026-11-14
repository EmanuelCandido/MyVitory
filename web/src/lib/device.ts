/**
 * Nível de movimento do aparelho (o mesmo critério de DevicePerformance no app):
 * - off: o sistema pede menos movimento;
 * - lite: PC ou celular fraco (≤4 núcleos ou ≤4 GB) ou economia de dados ligada: só retorno de toque;
 * - full: o resto.
 */
export type MotionLevel = 'full' | 'lite' | 'off'

type NavigatorHints = Navigator & { deviceMemory?: number; connection?: { saveData?: boolean } }

export function motionLevel(): MotionLevel {
  if (typeof window === 'undefined') return 'full'
  if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return 'off'
  const nav = navigator as NavigatorHints
  if ((nav.hardwareConcurrency ?? 8) <= 4 || (nav.deviceMemory ?? 8) <= 4 || saveData()) return 'lite'
  return 'full'
}

/** Economia de dados ligada: nada de baixar GIF só para miniatura. */
export const saveData = () => (typeof navigator !== 'undefined' && (navigator as NavigatorHints).connection?.saveData === true)

/** Lido do <html>, que main.tsx preenche antes do primeiro render. */
export const currentMotion = (): MotionLevel => (document.documentElement.dataset.motion as MotionLevel | undefined) ?? 'full'
