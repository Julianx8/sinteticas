import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { formatError } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { AuthLayout } from './AuthLayout'

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const fromPath = (location.state as { from?: string } | null)?.from ?? '/'

  const [values, setValues] = useState({ fullName: '', email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    return <Navigate to={fromPath} replace />
  }

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      if (mode === 'login') {
        await login(values.email, values.password)
      } else {
        await register(values.fullName, values.email, values.password)
      }
      navigate(fromPath, { replace: true })
    } catch (caughtError) {
      setError(formatError(caughtError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout eyebrow={mode === 'login' ? 'Ingresar' : 'Registro'} title={mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}>
      <form onSubmit={submit} className="auth-form">
        {mode === 'register' && (
          <label>
            Nombre completo
            <input
              type="text"
              value={values.fullName}
              onChange={(event) => setValues((current) => ({ ...current, fullName: event.target.value }))}
              placeholder="Tu nombre"
              autoComplete="name"
              maxLength={150}
              required
            />
          </label>
        )}

        <label>
          Email
          <input
            type="email"
            value={values.email}
            onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))}
            placeholder="correo@ejemplo.com"
            autoComplete="email"
            required
          />
        </label>

        <label>
          Contraseña
          <input
            type="password"
            value={values.password}
            onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
            placeholder="••••••••"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            minLength={mode === 'register' ? 8 : undefined}
            maxLength={72}
            required
          />
          {mode === 'register' && <small className="muted">Mínimo 8 caracteres.</small>}
        </label>

        {error && <div className="status-box error">{error}</div>}

        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Procesando...' : mode === 'login' ? 'Entrar' : 'Registrarme'}
        </button>
      </form>

      {mode === 'login' && (
        <p className="switch-text">
          <Link to="/olvide-contrasena">¿Olvidaste tu contraseña?</Link>
        </p>
      )}
      <p className="switch-text">
        {mode === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?'}{' '}
        <Link to={mode === 'login' ? '/register' : '/login'} state={location.state}>
          {mode === 'login' ? 'Regístrate aquí' : 'Inicia sesión'}
        </Link>
      </p>
    </AuthLayout>
  )
}
