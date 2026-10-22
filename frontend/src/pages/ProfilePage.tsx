import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'
import type { User } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ErrorBox, PageHeading, SuccessBox } from '../components/ui'
import { ROLE_LABELS, formatDateTime } from '../lib/format'

export function ProfilePage() {
  const { user, updateUser } = useAuth()
  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: () => apiFetch<User>('/api/v1/users/me'),
  })
  const profile = profileQuery.data ?? user

  // Roles o estado pudieron cambiar desde el último login
  useEffect(() => {
    if (profileQuery.data) updateUser(profileQuery.data)
  }, [profileQuery.data])

  if (!profile) return null

  return (
    <section className="content-panel profile-page">
      <PageHeading eyebrow="Cuenta" title="Mi perfil" />

      <div className="profile-card profile-layout">
        <div className="profile-hero">
          <div className="avatar-badge">{profile.fullName.charAt(0).toUpperCase()}</div>
          <div>
            <h2>{profile.fullName}</h2>
            <p>{profile.email}</p>
          </div>
        </div>
        <div className="role-list">
          {profile.roles.map((role) => <span key={role} className="role-pill">{ROLE_LABELS[role]}</span>)}
        </div>
        <div className="info-grid">
          <div className="info-chip">
            <p className="label">Miembro desde</p>
            <strong>{formatDateTime(profile.createdAt)}</strong>
          </div>
        </div>
      </div>

      <div className="admin-grid">
        <div className="admin-card">
          <h3>Datos personales</h3>
          <NameForm current={profile.fullName} onSaved={updateUser} />
        </div>
        <div className="admin-card">
          <h3>Cambiar contraseña</h3>
          <PasswordForm email={profile.email} />
        </div>
      </div>

      <div className="admin-card">
        <h3>Sesiones</h3>
        <SessionsSection />
      </div>
    </section>
  )
}

function NameForm({ current, onSaved }: { current: string; onSaved: (user: User) => void }) {
  const [fullName, setFullName] = useState(current)
  const mutation = useMutation({
    mutationFn: () => apiFetch<User>('/api/v1/users/me', { method: 'PATCH', body: { fullName: fullName.trim() } }),
    onSuccess: onSaved,
  })

  return (
    <form className="stack-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}>
      <label>
        Nombre completo
        <input value={fullName} maxLength={150} onChange={(event) => { setFullName(event.target.value); mutation.reset() }} required />
      </label>
      <ErrorBox error={mutation.error} />
      <SuccessBox show={mutation.isSuccess}>Nombre actualizado.</SuccessBox>
      <button type="submit" className="primary" disabled={mutation.isPending || fullName.trim() === current}>
        Guardar
      </button>
    </form>
  )
}

function PasswordForm({ email }: { email: string }) {
  const { login } = useAuth()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmation: '' })
  const [mismatch, setMismatch] = useState(false)

  const mutation = useMutation({
    mutationFn: async () => {
      await apiFetch('/api/v1/users/me/password', {
        method: 'PUT',
        body: { currentPassword: form.currentPassword, newPassword: form.newPassword },
      })
      // El backend cierra todas las sesiones al cambiar la contraseña: se abre una nueva aquí
      await login(email, form.newPassword)
    },
    onSuccess: () => setForm({ currentPassword: '', newPassword: '', confirmation: '' }),
  })

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setMismatch(form.newPassword !== form.confirmation)
    if (form.newPassword === form.confirmation) mutation.mutate()
  }
  const set = (field: keyof typeof form) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setForm((current) => ({ ...current, [field]: event.target.value }))

  return (
    <form className="stack-form" onSubmit={submit}>
      <label>
        Contraseña actual
        <input type="password" value={form.currentPassword} onChange={set('currentPassword')} autoComplete="current-password" required />
      </label>
      <label>
        Contraseña nueva
        <input type="password" value={form.newPassword} onChange={set('newPassword')} autoComplete="new-password" minLength={8} maxLength={72} required />
      </label>
      <label>
        Repite la contraseña nueva
        <input type="password" value={form.confirmation} onChange={set('confirmation')} autoComplete="new-password" minLength={8} maxLength={72} required />
      </label>
      {mismatch && <div className="status-box error sub-box">Las contraseñas nuevas no coinciden.</div>}
      <ErrorBox error={mutation.error} />
      <SuccessBox show={mutation.isSuccess}>Contraseña cambiada. Cerramos tus sesiones en otros dispositivos.</SuccessBox>
      <button type="submit" className="primary" disabled={mutation.isPending}>
        {mutation.isPending ? 'Guardando...' : 'Cambiar contraseña'}
      </button>
    </form>
  )
}

function SessionsSection() {
  const { logout } = useAuth()
  const mutation = useMutation({
    mutationFn: () => apiFetch('/api/v1/users/me/sessions', { method: 'DELETE' }),
    // También se cerró la de este navegador
    onSuccess: logout,
  })

  return (
    <div className="stack-form">
      <p className="muted">¿Iniciaste sesión en un computador ajeno o perdiste el celular? Cierra la sesión en todos los dispositivos, incluido este.</p>
      <ErrorBox error={mutation.error} />
      <button type="button" className="secondary" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
        Cerrar sesión en todos los dispositivos
      </button>
    </div>
  )
}
