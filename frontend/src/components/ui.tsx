import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { ReservationStatus } from '../api/types'
import { describeError } from '../lib/errors'
import { STATUS_LABELS, formatDateTime } from '../lib/format'

/** Cambia el título de la pestaña: "Mis reservas · Sintéticas". */
export function usePageTitle(title: string | undefined) {
  useEffect(() => {
    document.title = title ? `${title} · Sintéticas` : 'Sintéticas'
  }, [title])
}

/** Bloques grises con la forma del contenido mientras carga. El texto queda para lectores de pantalla. */
export function Skeleton({ label, rows = 3 }: { label: string; rows?: number }) {
  return (
    <div className="skeleton-list" role="status" aria-busy="true">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, index) => <div key={index} className="skeleton-row" aria-hidden="true" />)}
    </div>
  )
}

/** Estado de una consulta: cargando, error o vacío. Devuelve null cuando hay datos que mostrar. */
export function QueryState({ isLoading, error, isEmpty, loading, empty, onRetry }: {
  isLoading: boolean
  error: unknown
  isEmpty?: boolean
  loading: string
  empty?: string
  onRetry?: () => void
}) {
  if (isLoading) return <Skeleton label={loading} />
  if (error) return <ErrorBox error={error} onRetry={onRetry} />
  if (isEmpty && empty) return <div className="status-box">{empty}</div>
  return null
}

/**
 * Muestra un error con un título claro y, si aplica, la acción que lo resuelve
 * (reintentar, iniciar sesión, ir a mis reservas). Ver lib/errors.ts.
 */
export function ErrorBox({ error, onRetry, children }: { error: unknown; onRetry?: () => void; children?: React.ReactNode }) {
  if (!error) return null
  const friendly = describeError(error)
  return (
    <div className="status-box error sub-box" role="alert">
      <strong className="error-title">{friendly.title}</strong>
      {friendly.message && <span> {friendly.message}</span>}
      <div className="error-actions">
        {friendly.action === 'retry' && onRetry && <button type="button" className="secondary small-button" onClick={onRetry}>Reintentar</button>}
        {friendly.action === 'login' && <Link to="/login" className="secondary link-button small-button">Iniciar sesión</Link>}
        {friendly.action === 'my-reservations' && <Link to="/mis-reservas" className="secondary link-button small-button">Ver mis reservas</Link>}
        {children}
      </div>
    </div>
  )
}

export function SuccessBox({ show, children }: { show: boolean; children: React.ReactNode }) {
  return show ? <div className="status-box success sub-box" role="status">{children}</div> : null
}

/** Aviso cuando la página muestra datos guardados porque no hay conexión. */
export function OfflineNotice({ savedAt }: { savedAt?: string }) {
  if (!savedAt) return null
  return (
    <div className="status-box offline-notice" role="status">
      Sin conexión · mostrando lo que guardamos el {formatDateTime(savedAt)}. Puede no estar al día.
    </div>
  )
}

export function ReservationStatusTag({ status }: { status: ReservationStatus }) {
  const tone = status === 'CONFIRMED' || status === 'COMPLETED' ? 'online' : 'offline'
  return <span className={`status-tag ${tone}`}>{STATUS_LABELS[status]}</span>
}

export function ActiveTag({ active, on = 'Activa', off = 'Inactiva' }: { active: boolean; on?: string; off?: string }) {
  return <span className={`status-tag ${active ? 'online' : 'offline'}`}>{active ? on : off}</span>
}

export function PageHeading({ eyebrow, title, children }: { eyebrow: string; title: string; children?: React.ReactNode }) {
  usePageTitle(title)
  return (
    <div className="heading-row">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
      {children && <div className="heading-actions">{children}</div>}
    </div>
  )
}

export function DateRange({ from, to, onChange }: { from: string; to: string; onChange: (from: string, to: string) => void }) {
  return (
    <div className="date-picker-wrap inline-range">
      <label>
        Desde
        <input type="date" value={from} max={to} onChange={(event) => event.target.value && onChange(event.target.value, to)} />
      </label>
      <label>
        Hasta
        <input type="date" value={to} min={from} onChange={(event) => event.target.value && onChange(from, event.target.value)} />
      </label>
    </div>
  )
}

// ---------- Hoja inferior (diálogo) ----------

/**
 * Panel que sube desde abajo en celular (y se centra en escritorio). Usa <dialog> nativo:
 * el foco queda dentro, Escape lo cierra y el fondo no se puede tocar.
 */
export function Sheet({ open, onClose, title, children }: {
  open: boolean
  onClose: () => void
  title: string
  children: React.ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      className="sheet"
      aria-label={title}
      onClose={onClose}
      onClick={(event) => { if (event.target === ref.current) onClose() }} // tocar el fondo cierra
    >
      <div className="sheet-body">
        <div className="sheet-header">
          <h2>{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Cerrar">✕</button>
        </div>
        {children}
      </div>
    </dialog>
  )
}

// ---------- Confirmación (reemplaza window.confirm) ----------

type ConfirmOptions = { title: string; message?: string; confirmLabel?: string; danger?: boolean }
type ConfirmRequest = ConfirmOptions & { resolve: (ok: boolean) => void }

const ConfirmContext = createContext<(options: ConfirmOptions) => Promise<boolean>>(async () => false)

export function ConfirmProvider({ children }: { children: React.ReactNode }) {
  const [request, setRequest] = useState<ConfirmRequest | null>(null)
  const confirm = useCallback((options: ConfirmOptions) => new Promise<boolean>((resolve) => setRequest({ ...options, resolve })), [])
  const answer = (ok: boolean) => {
    request?.resolve(ok)
    setRequest(null)
  }

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      <Sheet open={request !== null} onClose={() => answer(false)} title={request?.title ?? ''}>
        {request?.message && <p className="muted">{request.message}</p>}
        <div className="sheet-actions">
          <button type="button" className="secondary" onClick={() => answer(false)}>Volver</button>
          <button type="button" className={request?.danger ? 'danger-button' : 'primary'} onClick={() => answer(true)} autoFocus>
            {request?.confirmLabel ?? 'Confirmar'}
          </button>
        </div>
      </Sheet>
    </ConfirmContext.Provider>
  )
}

/** const confirm = useConfirm(); if (await confirm({ title: '¿Cancelar?' })) ... */
export function useConfirm() {
  return useContext(ConfirmContext)
}
