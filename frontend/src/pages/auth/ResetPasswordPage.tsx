import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { apiFetch } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { ErrorBox } from '../../components/ui'
import { AuthLayout } from './AuthLayout'

/** Destino del enlace del correo: /restablecer-contrasena?token=… */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { isAuthenticated, logout } = useAuth()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [mismatch, setMismatch] = useState(false)

  const mutation = useMutation({
    mutationFn: () => apiFetch('/api/v1/auth/password/reset', { method: 'POST', body: { token, newPassword: password }, auth: false }),
    // El backend cierra todas las sesiones de la cuenta; también la de este navegador si estaba abierta
    onSuccess: () => {
      if (isAuthenticated) logout()
    },
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (password !== confirmation) {
      setMismatch(true)
      return
    }
    setMismatch(false)
    mutation.mutate()
  }

  if (!token) {
    return (
      <AuthLayout eyebrow="Recuperar acceso" title="Enlace incompleto">
        <div className="status-box error">El enlace no trae el código. Ábrelo de nuevo desde el correo o pide uno nuevo.</div>
        <p className="switch-text"><Link to="/olvide-contrasena">Pedir un enlace nuevo</Link></p>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout eyebrow="Recuperar acceso" title="Elige una contraseña nueva">
      {mutation.isSuccess ? (
        <>
          <div className="status-box success">Listo. Tu contraseña cambió y cerramos las sesiones abiertas en otros dispositivos.</div>
          <p className="switch-text"><Link to="/login" className="primary link-button">Iniciar sesión</Link></p>
        </>
      ) : (
        <form className="auth-form" onSubmit={submit}>
          <label>
            Contraseña nueva
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" minLength={8} maxLength={72} required />
            <small className="muted">Mínimo 8 caracteres.</small>
          </label>
          <label>
            Repite la contraseña
            <input type="password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="new-password" minLength={8} maxLength={72} required />
          </label>
          {mismatch && <div className="status-box error">Las contraseñas no coinciden.</div>}
          <ErrorBox error={mutation.error} />
          {mutation.error && (
            <p className="switch-text"><Link to="/olvide-contrasena">Pedir un enlace nuevo</Link></p>
          )}
          <button type="submit" className="primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Guardando...' : 'Cambiar contraseña'}
          </button>
        </form>
      )}
    </AuthLayout>
  )
}
