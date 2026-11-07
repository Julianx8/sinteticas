import { ApiError } from '../api/client'

/**
 * Qué puede hacer la interfaz ante un error, además de mostrar el mensaje:
 * - refresh-availability: recargar las horas (alguien reservó o la sede bloqueó) y sugerir otra
 * - my-reservations: llevar a "Mis reservas"
 * - login: volver a iniciar sesión
 * - retry: reintentar (sin conexión o error del servidor)
 */
export type ErrorAction = 'refresh-availability' | 'my-reservations' | 'login' | 'retry'

export type FriendlyError = {
  title: string
  message: string
  action?: ErrorAction
}

/** Códigos del backend (campo `code` del ProblemDetail) con su mensaje para la persona. */
const BY_CODE: Record<string, Omit<FriendlyError, 'message'> & { message?: string }> = {
  RESERVATION_SLOT_TAKEN: {
    title: '¡Uy! Alguien acaba de tomar esa hora',
    message: 'Actualizamos las horas libres para que elijas otra.',
    action: 'refresh-availability',
  },
  SLOT_BLOCKED: {
    title: 'La sede cerró ese horario',
    message: 'Puede ser mantenimiento o un torneo. Actualizamos las horas libres.',
    action: 'refresh-availability',
  },
  OUTSIDE_SCHEDULE: { title: 'Ese horario ya no está abierto', action: 'refresh-availability' },
  CROSSES_MIDNIGHT: { title: 'La reserva no puede pasar de la medianoche', action: 'refresh-availability' },
  RESERVATION_IN_PAST: { title: 'Esa hora ya pasó', message: 'Actualizamos las horas del día.', action: 'refresh-availability' },
  INVALID_START_TIME: { title: 'Esa hora ya no se puede reservar', action: 'refresh-availability' },
  TOO_FAR_AHEAD: { title: 'Solo se puede reservar hasta 30 días adelante' },
  DURATION_TOO_LONG: { title: 'La reserva puede durar máximo 3 horas' },
  TOO_MANY_ACTIVE_RESERVATIONS: {
    title: 'Llegaste al máximo de partidos pendientes',
    message: 'Puedes tener hasta 5 reservas por jugar. Cuando juegues o canceles una, podrás reservar otra.',
    action: 'my-reservations',
  },
  CANCELLATION_TOO_LATE: {
    title: 'Ya no se puede cancelar desde la app',
    message: 'Faltan menos de 2 horas para el partido. Comunícate con la sede.',
  },
  COURT_NOT_BOOKABLE: { title: 'Esta cancha no está recibiendo reservas', message: 'La sede la desactivó por ahora.' },
  RESERVATION_NOT_ACTIVE: { title: 'Esta reserva ya no está activa' },
  RESERVATION_ALREADY_STARTED: { title: 'El partido ya empezó' },
  SESSION_EXPIRED: { title: 'Tu sesión terminó', message: 'Vuelve a iniciar sesión para continuar.', action: 'login' },
  REFRESH_TOKEN_REUSED: { title: 'Tu sesión terminó', message: 'Por seguridad cerramos tu sesión. Vuelve a entrar.', action: 'login' },
  INVALID_REFRESH_TOKEN: { title: 'Tu sesión terminó', message: 'Vuelve a iniciar sesión para continuar.', action: 'login' },
  NETWORK_ERROR: { title: 'Sin conexión', message: 'Revisa tu internet e inténtalo de nuevo.', action: 'retry' },
}

/** Traduce cualquier error a un título, un mensaje y (si aplica) una acción. */
export function describeError(error: unknown): FriendlyError {
  if (error instanceof ApiError) {
    const known = error.code ? BY_CODE[error.code] : undefined
    if (known) return { title: known.title, message: known.message ?? error.message, action: known.action }
    if (error.status >= 500) {
      return { title: 'Algo falló de nuestro lado', message: 'Inténtalo de nuevo en un momento.', action: 'retry' }
    }
    // Los demás mensajes del backend ya vienen en español y son claros (validaciones, reglas)
    return { title: error.message, message: '' }
  }
  if (error instanceof Error) return { title: error.message, message: '' }
  return { title: 'No se pudo completar la operación.', message: '' }
}

export function errorCode(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined
}
