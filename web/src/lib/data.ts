import { useCallback, useEffect, useRef, useState } from 'react'
import { onAuthStateChanged, type User } from 'firebase/auth'
import {
  arrayRemove,
  arrayUnion,
  collection,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  limit,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore'
import { auth, db } from '../firebase'
import { decodePlan, encodePlan } from '../sync/codec'
import { mergePlans, samePlan } from '../sync/merge'
import type { DayDoc, PlanSnapshot, WeightPoint } from '../sync/types'

/*
 * Onde cada coisa fica no Firestore (o mesmo do app e de firebase/firestore.rules):
 *   users/{uid}                          atletas que o treinador acompanha
 *   athletes/{id}                        ficha: nome, treinadores, último sync do celular
 *   athletes/{id}/plan/current           o plano (lido e escrito pelos dois lados)
 *   athletes/{id}/days/{aaaa-mm-dd}      um documento por dia de treino (só o celular escreve)
 *   athletes/{id}/private/weights        pesagens, só quando a atleta compartilha
 *   invites/{código}                     código de pareamento gerado no celular
 */

export function useUser(): User | null | undefined {
  const [user, setUser] = useState<User | null | undefined>(undefined)
  useEffect(() => onAuthStateChanged(auth, setUser), [])
  return user
}

export interface Athlete {
  id: string
  name: string
  lastSyncAt: Date | null
  appVersion: string | null
  shareWeight: boolean
  /** A atleta tirou este treinador (ou desconectou o painel). */
  lost: boolean
}

/** Atletas que o treinador acompanha, ao vivo. */
export function useAthletes(uid: string): Athlete[] | undefined {
  const [ids, setIds] = useState<string[] | undefined>(undefined)
  const [byId, setById] = useState<Record<string, Athlete>>({})

  useEffect(
    () =>
      onSnapshot(
        doc(db, 'users', uid),
        (snap) => setIds((snap.get('athletes') as string[] | undefined) ?? []),
        () => setIds([]),
      ),
    [uid],
  )

  useEffect(() => {
    if (!ids) return
    const stops = ids.map((id) =>
      onSnapshot(
        doc(db, 'athletes', id),
        (snap) => {
          const coaches = (snap.get('coaches') as Record<string, unknown> | undefined) ?? {}
          setById((prev) => ({
            ...prev,
            [id]: {
              id,
              name: (snap.get('name') as string | undefined) ?? 'Atleta',
              lastSyncAt: (snap.get('lastSyncAt') as Timestamp | undefined)?.toDate() ?? null,
              appVersion: (snap.get('appVersion') as string | undefined) ?? null,
              shareWeight: Boolean(snap.get('shareWeight')),
              lost: !snap.exists() || !(uid in coaches),
            },
          }))
        },
        () => setById((prev) => ({ ...prev, [id]: { id, name: prev[id]?.name ?? 'Atleta', lastSyncAt: null, appVersion: null, shareWeight: false, lost: true } })),
      ),
    )
    return () => stops.forEach((stop) => stop())
  }, [ids])

  // Só devolve a lista quando todas as fichas chegaram (evita mostrar "nenhuma atleta" por um instante).
  if (!ids || ids.some((id) => !byId[id])) return undefined
  return ids.map((id) => byId[id])
}

export const normalizeCode = (code: string) => code.toUpperCase().replace(/[^A-Z0-9]/g, '')

/** Usa o código gerado no celular da atleta para passar a acompanhar o treino dela. */
export async function pairWithCode(user: User, rawCode: string): Promise<string> {
  const code = normalizeCode(rawCode)
  if (code.length !== 8) throw new Error('O código tem 8 letras e números.')
  const invite = await getDoc(doc(db, 'invites', code)).catch(() => null)
  if (!invite?.exists()) throw new Error('Código não encontrado. Confira ou gere outro no celular.')
  const expiresAt = (invite.get('expiresAt') as Timestamp).toMillis()
  if (expiresAt < Date.now()) throw new Error('Esse código expirou. Gere outro no celular (Ajustes › Painel do treinador).')
  const athleteId = invite.get('athleteId') as string

  await updateDoc(doc(db, 'athletes', athleteId), {
    [`coaches.${user.uid}`]: { name: user.displayName ?? 'Treinador', email: user.email ?? '', addedAt: serverTimestamp() },
    pairCode: code,
  })
  await setDoc(doc(db, 'users', user.uid), { athletes: arrayUnion(athleteId) }, { merge: true })
  await deleteDoc(doc(db, 'invites', code)).catch(() => undefined)
  return athleteId
}

/** Para de acompanhar a atleta (ela continua com tudo no celular). */
export async function leaveAthlete(user: User, athleteId: string, lost: boolean) {
  if (!lost) await updateDoc(doc(db, 'athletes', athleteId), { [`coaches.${user.uid}`]: deleteField() })
  await setDoc(doc(db, 'users', user.uid), { athletes: arrayRemove(athleteId) }, { merge: true })
}

export type SaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'error'

export interface PlanState {
  status: 'loading' | 'empty' | 'ready' | 'denied'
  plan: PlanSnapshot | null
  save: SaveState
  error: string | null
  /** Quem gravou a versão da nuvem por último e quando. */
  updatedBy: 'app' | 'web' | null
  savedAt: Date | null
  edit: (change: (plan: PlanSnapshot) => PlanSnapshot) => void
}

const SAVE_DELAY_MS = 700

/**
 * O plano ao vivo, com as mudanças do painel salvas sozinhas (em transação, mesclando com o que o celular
 * mandou no meio tempo). Mudança feita no celular aparece aqui sem perder o que ainda não foi salvo.
 */
export function usePlan(athleteId: string): PlanState {
  const ref = doc(db, 'athletes', athleteId, 'plan', 'current')
  const [status, setStatus] = useState<PlanState['status']>('loading')
  const [plan, setPlan] = useState<PlanSnapshot | null>(null)
  const [save, setSave] = useState<SaveState>('idle')
  const [error, setError] = useState<string | null>(null)
  const [meta, setMeta] = useState<{ updatedBy: PlanState['updatedBy']; savedAt: Date | null }>({ updatedBy: null, savedAt: null })

  const draft = useRef<PlanSnapshot | null>(null)
  const version = useRef(0)
  const savedVersion = useRef(0)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const saving = useRef(false)

  const apply = (next: PlanSnapshot) => {
    draft.current = next
    setPlan(next)
  }

  useEffect(() => {
    setStatus('loading')
    draft.current = null
    version.current = 0
    savedVersion.current = 0
    return onSnapshot(
      ref,
      (snap) => {
        if (!snap.exists()) {
          setStatus('empty')
          return
        }
        const cloud = decodePlan(snap.data())
        setMeta({ updatedBy: (snap.get('updatedBy') as PlanState['updatedBy']) ?? null, savedAt: (snap.get('savedAt') as Timestamp | null)?.toDate() ?? null })
        // Com mudança ainda não salva, junta; sem, a nuvem manda.
        const dirty = version.current !== savedVersion.current
        apply(dirty && draft.current ? mergePlans(draft.current, cloud, Date.now()) : cloud)
        setStatus('ready')
      },
      (e) => {
        setStatus(e.code === 'permission-denied' ? 'denied' : 'loading')
        setError(e.message)
      },
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [athleteId])

  const flush = useCallback(async () => {
    if (saving.current || !draft.current) return
    saving.current = true
    const target = version.current
    setSave('saving')
    try {
      const local = draft.current
      const merged = await runTransaction(db, async (tx) => {
        const snap = await tx.get(ref)
        const latest = snap.exists() ? decodePlan(snap.data()) : null
        const result = latest ? mergePlans(local, latest, Date.now()) : local
        if (!samePlan(result, latest)) tx.set(ref, { ...encodePlan(result), updatedBy: 'web', savedAt: serverTimestamp() })
        return result
      })
      savedVersion.current = target
      // Mudou mais coisa enquanto salvava: junta e salva de novo.
      if (version.current !== target && draft.current) {
        apply(mergePlans(draft.current, merged, Date.now()))
        timer.current = setTimeout(() => void flush(), SAVE_DELAY_MS)
      } else {
        apply(merged)
        setSave('saved')
      }
      setError(null)
    } catch (e) {
      setSave('error')
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      saving.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [athleteId])

  const edit = useCallback(
    (change: (plan: PlanSnapshot) => PlanSnapshot) => {
      if (!draft.current) return
      const next = change(draft.current)
      if (next === draft.current) return
      version.current++
      apply(next)
      setSave('pending')
      if (timer.current) clearTimeout(timer.current)
      timer.current = setTimeout(() => void flush(), SAVE_DELAY_MS)
    },
    [flush],
  )

  // Fechar a aba com mudança pendente: o navegador pergunta antes.
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => {
      if (version.current !== savedVersion.current) e.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [])

  return { status, plan, save, error, updatedBy: meta.updatedBy, savedAt: meta.savedAt, edit }
}

/** Dias de treino que o celular enviou (mais recentes primeiro). */
export function useDays(athleteId: string): DayDoc[] | undefined {
  const [days, setDays] = useState<DayDoc[] | undefined>(undefined)
  useEffect(() => {
    setDays(undefined)
    return onSnapshot(
      query(collection(db, 'athletes', athleteId, 'days'), orderBy('date', 'desc'), limit(200)),
      (snap) => setDays(snap.docs.map((d) => d.data() as DayDoc)),
      () => setDays([]),
    )
  }, [athleteId])
  return days
}

/** Pesagens, quando a atleta compartilha. null: não compartilha. */
export function useWeights(athleteId: string): WeightPoint[] | null | undefined {
  const [points, setPoints] = useState<WeightPoint[] | null | undefined>(undefined)
  useEffect(() => {
    setPoints(undefined)
    return onSnapshot(
      doc(db, 'athletes', athleteId, 'private', 'weights'),
      (snap) => setPoints(snap.exists() ? ((snap.get('points') as WeightPoint[] | undefined) ?? []) : null),
      () => setPoints(null),
    )
  }, [athleteId])
  return points
}
