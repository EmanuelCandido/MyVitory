export const DAYS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']
export const DAYS_SHORT = ['SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB', 'DOM']
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

/** "2026-10-31" → Date local (sem fuso deslocar o dia). */
export const parseDay = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export const isoDay = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

/** 1 = segunda ... 7 = domingo (como o app). */
export const dayOfWeek = (date: Date) => ((date.getDay() + 6) % 7) + 1

export const weekStart = (date: Date) => {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  d.setDate(d.getDate() - (dayOfWeek(d) - 1))
  return d
}

export const addDays = (date: Date, days: number) => {
  const d = new Date(date)
  d.setDate(d.getDate() + days)
  return d
}

/** "31 out" */
export const shortDate = (date: Date) => `${date.getDate()} ${MONTHS[date.getMonth()]}`

/** "sex, 31 out" */
export const dayLabel = (date: Date) => `${DAYS_SHORT[dayOfWeek(date) - 1].toLowerCase()}, ${shortDate(date)}`

/** "1:30" */
export const clock = (seconds: number) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`

export const kg = (value: number) => (Number.isInteger(value) ? String(value) : value.toFixed(1).replace('.', ','))

/** "há 5 min", "há 2 h", "ontem às 18:20", "12/10 às 09:00" */
export function relative(date: Date, now = new Date()): string {
  const minutes = Math.round((now.getTime() - date.getTime()) / 60_000)
  if (minutes < 1) return 'agora'
  if (minutes < 60) return `há ${minutes} min`
  if (minutes < 12 * 60) return `há ${Math.round(minutes / 60)} h`
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  const yesterday = addDays(now, -1)
  if (date.toDateString() === now.toDateString()) return `hoje às ${time}`
  if (date.toDateString() === yesterday.toDateString()) return `ontem às ${time}`
  return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')} às ${time}`
}

/** "hoje", "ontem", "há 3 dias", "31 out" (a partir de duas semanas). */
export function daysAgo(iso: string, now = new Date()): string {
  const diff = Math.round((parseDay(isoDay(now)).getTime() - parseDay(iso).getTime()) / 86_400_000)
  if (diff <= 0) return 'hoje'
  if (diff === 1) return 'ontem'
  if (diff < 14) return `há ${diff} dias`
  return shortDate(parseDay(iso))
}

export const plural =(n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`
