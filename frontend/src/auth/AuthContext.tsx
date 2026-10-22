import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { SESSION_EVENT, apiFetch, queryClient, readSession, sessionFrom, writeSession, type StoredSession } from '../api/client'
import type { AuthResponse, Role, User } from '../api/types'

type AuthContextValue = {
  user: User | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (fullName: string, email: string, password: string) => Promise<void>
  logout: () => void
  /** Reemplaza el usuario guardado (p. ej. tras editar el perfil) */
  updateUser: (user: User) => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return context
}

export function hasAnyRole(user: User | null, ...roles: Role[]) {
  return Boolean(user && roles.some((role) => user.roles.includes(role)))
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<StoredSession>(readSession)

  useEffect(() => {
    const sync = () => setSession(readSession())
    window.addEventListener(SESSION_EVENT, sync)
    // Otra pestaña inició o cerró sesión
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener(SESSION_EVENT, sync)
      window.removeEventListener('storage', sync)
    }
  }, [])

  const value = useMemo<AuthContextValue>(() => {
    const startSession = (response: AuthResponse) => {
      // Lo que se consultó con la sesión anterior no le corresponde al nuevo usuario
      queryClient.clear()
      writeSession(sessionFrom(response))
    }

    return {
      user: session.user,
      isAuthenticated: Boolean(session.token && session.user),
      login: async (email, password) => {
        startSession(await apiFetch<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: { email, password }, auth: false }))
      },
      register: async (fullName, email, password) => {
        startSession(await apiFetch<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: { fullName, email, password }, auth: false }))
      },
      logout: () => {
        const { refreshToken } = readSession()
        if (refreshToken) {
          // Revoca la sesión en el backend; si falla, igual se cierra en este navegador
          apiFetch('/api/v1/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }).catch(() => undefined)
        }
        writeSession(null)
        queryClient.clear()
      },
      updateUser: (user) => writeSession({ ...readSession(), user }),
    }
  }, [session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** Exige sesión; sin ella manda al login y vuelve a esta página después. */
export function RequireAuth({ children, roles }: { children: React.ReactNode; roles?: Role[] }) {
  const { user, isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (roles && !hasAnyRole(user, ...roles)) {
    return <div className="status-box error">No tienes permisos para ver esta página.</div>
  }
  return <>{children}</>
}
