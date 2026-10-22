import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../../api/client'
import { ErrorBox } from '../../components/ui'
import { AuthLayout } from './AuthLayout'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const mutation = useMutation({
    mutationFn: () => apiFetch('/api/v1/auth/password/forgot', { method: 'POST', body: { email }, auth: false }),
  })

  return (
    <AuthLayout eyebrow="Recuperar acceso" title="¿Olvidaste tu contraseña?">
      {mutation.isSuccess ? (
        <>
          {/* El backend responde igual exista o no la cuenta: el mensaje tampoco lo revela */}
          <div className="status-box success">
            Si <strong>{email}</strong> tiene una cuenta, te enviamos un enlace para elegir una contraseña nueva.
            Vence en 1 hora. Revisa también la carpeta de spam.
          </div>
          <p className="switch-text">
            <Link to="/login">Volver a iniciar sesión</Link>
          </p>
        </>
      ) : (
        <>
          <p className="muted">Escribe el email de tu cuenta y te enviaremos un enlace para restablecerla.</p>
          <form className="auth-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}>
            <label>
              Email
              <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="correo@ejemplo.com" autoComplete="email" required />
            </label>
            <ErrorBox error={mutation.error} />
            <button type="submit" className="primary" disabled={mutation.isPending}>
              {mutation.isPending ? 'Enviando...' : 'Enviar enlace'}
            </button>
          </form>
          <p className="switch-text">
            <Link to="/login">Volver a iniciar sesión</Link>
          </p>
        </>
      )}
    </AuthLayout>
  )
}
