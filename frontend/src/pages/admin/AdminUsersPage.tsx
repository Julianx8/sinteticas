import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { apiFetch, query, queryClient } from '../../api/client'
import type { PageResponse, Role, User } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { ActiveTag, ErrorBox, PageHeading, QueryState } from '../../components/ui'
import { ROLE_LABELS, formatDateTime } from '../../lib/format'

export function AdminUsersPage() {
  const { user: me } = useAuth()
  const [search, setSearch] = useState('')
  const [role, setRole] = useState<Role | ''>('')
  const [page, setPage] = useState(0)

  const usersQuery = useQuery({
    queryKey: ['admin-users', search, role, page],
    queryFn: () => apiFetch<PageResponse<User>>(`/api/v1/admin/users${query({ q: search.trim(), role, page, size: 20, sort: 'createdAt,desc' })}`),
    placeholderData: (previous) => previous,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] })

  const statusMutation = useMutation({
    mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
      apiFetch<User>(`/api/v1/admin/users/${userId}/status`, { method: 'PUT', body: { enabled } }),
    onSuccess: refresh,
  })
  const rolesMutation = useMutation({
    mutationFn: ({ userId, roles }: { userId: string; roles: Role[] }) =>
      apiFetch<User>(`/api/v1/admin/users/${userId}/roles`, { method: 'PUT', body: { roles } }),
    onSuccess: refresh,
  })

  const users = usersQuery.data?.content ?? []
  const totalPages = usersQuery.data?.totalPages ?? 0
  const toggleOwner = (person: User) => {
    const roles: Role[] = person.roles.includes('ADMIN')
      ? person.roles.filter((current) => current !== 'ADMIN')
      : [...person.roles, 'ADMIN']
    rolesMutation.mutate({ userId: person.id, roles })
  }

  return (
    <section className="content-panel routes-page">
      <PageHeading eyebrow="Super admin" title="Usuarios y roles" />
      <p className="muted">Para que alguien pueda crear sedes, dale el rol «Dueño de sede». El cambio aplica la próxima vez que su sesión se renueve (máximo 15 minutos).</p>

      <div className="filter-row">
        <input type="search" value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} placeholder="Buscar por nombre o email" aria-label="Buscar usuarios" />
        <select value={role} onChange={(event) => { setRole(event.target.value as Role | ''); setPage(0) }} aria-label="Rol">
          <option value="">Todos los roles</option>
          {Object.entries(ROLE_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div>

      <QueryState isLoading={usersQuery.isLoading} error={usersQuery.error} isEmpty={users.length === 0} loading="Cargando usuarios..." empty="No hay usuarios con ese filtro." />
      <ErrorBox error={statusMutation.error ?? rolesMutation.error} />

      <div className="admin-list-card mini-list">
        {users.map((person) => {
          const isMe = person.id === me?.id
          return (
            <div key={person.id} className="mini-item user-item">
              <div>
                <strong>{person.fullName}{isMe && ' (tú)'}</strong>
                <p>{person.email} · desde {formatDateTime(person.createdAt)}</p>
                <div className="role-list compact-role-list">
                  {person.roles.map((current) => <span key={current} className="role-pill">{ROLE_LABELS[current]}</span>)}
                  <ActiveTag active={person.enabled} on="Activo" off="Desactivado" />
                </div>
              </div>
              {!isMe && !person.roles.includes('SUPER_ADMIN') && (
                <div className="user-actions">
                  <button type="button" className="secondary small-button" disabled={rolesMutation.isPending} onClick={() => toggleOwner(person)}>
                    {person.roles.includes('ADMIN') ? 'Quitar dueño' : 'Hacer dueño'}
                  </button>
                  <button
                    type="button"
                    className="secondary small-button"
                    disabled={statusMutation.isPending}
                    onClick={() => (person.enabled ? window.confirm(`¿Desactivar a ${person.fullName}? Se cerrarán sus sesiones y no podrá entrar.`) : true)
                      && statusMutation.mutate({ userId: person.id, enabled: !person.enabled })}
                  >
                    {person.enabled ? 'Desactivar' : 'Activar'}
                  </button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {totalPages > 1 && (
        <div className="pager">
          <button type="button" className="secondary small-button" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
          <span className="muted">Página {page + 1} de {totalPages}</span>
          <button type="button" className="secondary small-button" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
        </div>
      )}
    </section>
  )
}
