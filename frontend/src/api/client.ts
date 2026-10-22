import { QueryClient } from '@tanstack/react-query'
import type { AuthResponse, User } from './types'

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const AUTH_STORAGE_KEY = 'sinteticas-auth'
// Avisa al AuthProvider cuando la sesión cambia (login, renovación, cierre)
export const SESSION_EVENT = 'sinteticas-session'

export const queryClient = new QueryClient({
  defaultOptions: {
    // Un 401/403/404 no se arregla reintentando
    queries: { retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 2 },
  },
})

// ---------- Sesión ----------

export type StoredSession = {
  token: string | null
  refreshToken: string | null
  user: User | null
}

const EMPTY_SESSION: StoredSession = { token: null, refreshToken: null, user: null }

export function readSession(): StoredSession {
  if (typeof window === 'undefined') return EMPTY_SESSION
  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return EMPTY_SESSION
    const parsed = JSON.parse(raw) as Partial<StoredSession>
    return { token: parsed.token ?? null, refreshToken: parsed.refreshToken ?? null, user: parsed.user ?? null }
  } catch {
    return EMPTY_SESSION
  }
}

export function writeSession(session: StoredSession | null) {
  try {
    if (session) {
      window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
    } else {
      window.localStorage.removeItem(AUTH_STORAGE_KEY)
    }
  } catch {
    // Sin almacenamiento la sesión dura solo mientras la pestaña esté abierta
  }
  window.dispatchEvent(new Event(SESSION_EVENT))
}

export function sessionFrom(response: AuthResponse): StoredSession {
  return { token: response.accessToken, refreshToken: response.refreshToken, user: response.user }
}

// ---------- Errores ----------

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) {
    super(message)
  }
}

type ErrorPayload = {
  status?: number
  title?: string
  detail?: string
  code?: string
  errors?: Record<string, string>
}

export function formatError(error: unknown): string {
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  return 'No se pudo completar la operación.'
}

// ---------- Peticiones ----------

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** false en login/registro/refresh: un token vencido haría que el backend responda 401 */
  auth?: boolean
}

// Varias peticiones pueden recibir 401 a la vez: todas esperan la misma renovación,
// porque el backend rota el refresh token y reutilizar uno ya usado cierra la sesión.
let refreshInFlight: Promise<string | null> | null = null

function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const { refreshToken } = readSession()
      if (!refreshToken) return null
      try {
        const response = await request<AuthResponse>('/api/v1/auth/refresh', { method: 'POST', body: { refreshToken } }, null)
        writeSession(sessionFrom(response))
        return response.accessToken
      } catch {
        writeSession(null)
        return null
      }
    })().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

/**
 * Llama a la API con el token de la sesión. Si el token de acceso venció (dura 15 min),
 * lo renueva una vez con el refresh token y repite la petición.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.auth === false ? null : readSession().token
  try {
    return await request<T>(path, options, token)
  } catch (error) {
    if (!token || !(error instanceof ApiError) || error.status !== 401) throw error
    const renewed = await refreshAccessToken()
    if (!renewed) throw new ApiError('Tu sesión expiró. Inicia sesión de nuevo.', 401, 'SESSION_EXPIRED')
    return request<T>(path, options, renewed)
  }
}

async function request<T>(path: string, options: RequestOptions, token: string | null): Promise<T> {
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  const text = await response.text()
  let payload: unknown = null
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = text
    }
  }

  if (!response.ok) {
    const error = payload as ErrorPayload | null
    const fieldErrors = error?.errors ? Object.values(error.errors).join('. ') : ''
    const message = [error?.detail, fieldErrors].filter(Boolean).join(' — ') || `Error al consultar la API (${response.status})`
    throw new ApiError(message, response.status, error?.code)
  }

  return payload as T
}

/** Arma "?a=1&b=2" omitiendo los valores vacíos. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const result = search.toString()
  return result ? `?${result}` : ''
}
