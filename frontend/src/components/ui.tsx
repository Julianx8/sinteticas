import { formatError } from '../api/client'
import type { ReservationStatus } from '../api/types'
import { STATUS_LABELS } from '../lib/format'

/** Estado de una consulta: cargando, error o vacío. Devuelve null cuando hay datos que mostrar. */
export function QueryState({ isLoading, error, isEmpty, loading, empty }: {
  isLoading: boolean
  error: unknown
  isEmpty?: boolean
  loading: string
  empty?: string
}) {
  if (isLoading) return <div className="status-box">{loading}</div>
  if (error) return <div className="status-box error">{formatError(error)}</div>
  if (isEmpty && empty) return <div className="status-box">{empty}</div>
  return null
}

export function ErrorBox({ error }: { error: unknown }) {
  return error ? <div className="status-box error sub-box">{formatError(error)}</div> : null
}

export function SuccessBox({ show, children }: { show: boolean; children: React.ReactNode }) {
  return show ? <div className="status-box success sub-box">{children}</div> : null
}

export function ReservationStatusTag({ status }: { status: ReservationStatus }) {
  const tone = status === 'CONFIRMED' || status === 'COMPLETED' ? 'online' : 'offline'
  return <span className={`status-tag ${tone}`}>{STATUS_LABELS[status]}</span>
}

export function ActiveTag({ active, on = 'Activa', off = 'Inactiva' }: { active: boolean; on?: string; off?: string }) {
  return <span className={`status-tag ${active ? 'online' : 'offline'}`}>{active ? on : off}</span>
}

export function PageHeading({ eyebrow, title, children }: { eyebrow: string; title: string; children?: React.ReactNode }) {
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
