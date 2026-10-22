import type { CourtType, ReservationStatus, Role } from '../api/types'

// El negocio funciona en hora de Colombia (UTC-5, sin horario de verano)
const TIME_ZONE = 'America/Bogota'
const BOGOTA_OFFSET = '-05:00'

export const DAY_NAMES: Record<number, string> = {
  1: 'Lunes', 2: 'Martes', 3: 'Miércoles', 4: 'Jueves', 5: 'Viernes', 6: 'Sábado', 7: 'Domingo',
}

export const DAY_SHORT: Record<number, string> = {
  1: 'Lun', 2: 'Mar', 3: 'Mié', 4: 'Jue', 5: 'Vie', 6: 'Sáb', 7: 'Dom',
}

export const STATUS_LABELS: Record<ReservationStatus, string> = {
  CONFIRMED: 'Confirmada',
  COMPLETED: 'Completada',
  CANCELLED: 'Cancelada',
  NO_SHOW: 'No asistió',
}

export const ROLE_LABELS: Record<Role, string> = {
  CUSTOMER: 'Cliente',
  ADMIN: 'Dueño de sede',
  SUPER_ADMIN: 'Super admin',
}

export function courtTypeLabel(type: CourtType): string {
  return `Fútbol ${type.replace('FUTBOL_', '')}`
}

export function formatPrice(value: number): string {
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(value)
}

export function formatPercent(rate: number | undefined): string {
  return rate === undefined ? '—' : `${(rate * 100).toFixed(1)} %`
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: TIME_ZONE }).format(new Date(value))
}

export function formatTime(value: string): string {
  return new Intl.DateTimeFormat('es-CO', { timeStyle: 'short', timeZone: TIME_ZONE }).format(new Date(value))
}

/** "2026-10-28" → "mié, 28 oct 2026" */
export function formatDate(isoDate: string): string {
  return new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${isoDate}T00:00:00Z`))
}

export function formatHour(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

/** "2026-12-24T10:00" (input datetime-local) → "2026-12-24T10:00:00-05:00" */
export function withBogotaOffset(localDateTime: string): string {
  return localDateTime.length === 16 ? `${localDateTime}:00${BOGOTA_OFFSET}` : `${localDateTime}${BOGOTA_OFFSET}`
}

/** Fecha de hoy en Colombia, "YYYY-MM-DD" (sin importar la zona del navegador) */
export function todayIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(new Date())
}

/** Suma días a una fecha "YYYY-MM-DD" */
export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}
