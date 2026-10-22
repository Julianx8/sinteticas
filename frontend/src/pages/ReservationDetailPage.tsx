import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, queryClient } from '../api/client'
import type { Reservation } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ErrorBox, PageHeading, QueryState, ReservationStatusTag } from '../components/ui'
import { formatDateTime, formatPrice } from '../lib/format'

export function ReservationDetailPage() {
  const { reservationId = '' } = useParams()
  const { user } = useAuth()
  const [reason, setReason] = useState('')

  const reservationQuery = useQuery({
    queryKey: ['reservations', 'detail', reservationId],
    queryFn: () => apiFetch<Reservation>(`/api/v1/reservations/${reservationId}`),
  })

  const update = (reservation: Reservation) => {
    queryClient.setQueryData(['reservations', 'detail', reservationId], reservation)
    queryClient.invalidateQueries({ queryKey: ['reservations'] })
    queryClient.invalidateQueries({ queryKey: ['availability'] })
    queryClient.invalidateQueries({ queryKey: ['venue-agenda'] })
  }

  const cancelMutation = useMutation({
    mutationFn: () => apiFetch<Reservation>(`/api/v1/reservations/${reservationId}/cancel`, {
      method: 'POST',
      body: { reason: reason.trim() || undefined },
    }),
    onSuccess: update,
  })
  const outcomeMutation = useMutation({
    mutationFn: (outcome: 'complete' | 'no-show') =>
      apiFetch<Reservation>(`/api/v1/reservations/${reservationId}/${outcome}`, { method: 'POST' }),
    onSuccess: update,
  })

  const reservation = reservationQuery.data
  if (!reservation) {
    return <QueryState isLoading={reservationQuery.isLoading} error={reservationQuery.error} loading="Cargando reserva..." />
  }

  const isMine = reservation.customer.id === user?.id
  const isActive = reservation.status === 'CONFIRMED'
  const hasStarted = new Date(reservation.startAt) <= new Date()

  return (
    <section className="content-panel detail-page">
      <PageHeading eyebrow="Reserva" title={`${reservation.court.name} · ${reservation.court.venueName}`}>
        <Link to={isMine ? '/mis-reservas' : `/admin/sedes/${reservation.court.venueId}/agenda`} className="secondary link-button">Volver</Link>
      </PageHeading>

      <div className="profile-card profile-layout">
        <div className="role-list">
          <ReservationStatusTag status={reservation.status} />
        </div>

        <div className="info-grid">
          <Info label="Inicio" value={formatDateTime(reservation.startAt)} />
          <Info label="Fin" value={formatDateTime(reservation.endAt)} />
          <Info label="Duración" value={`${reservation.durationHours} hora${reservation.durationHours > 1 ? 's' : ''}`} />
          <Info label="Total" value={formatPrice(reservation.totalPrice)} />
          <Info label="Dirección" value={reservation.court.venueAddress} />
          {!isMine && <Info label="Cliente" value={`${reservation.customer.fullName} · ${reservation.customer.email}`} />}
          {reservation.notes && <Info label="Notas" value={reservation.notes} />}
          {reservation.cancelledAt && (
            <Info label="Cancelada" value={`${formatDateTime(reservation.cancelledAt)}${reservation.cancellationReason ? ` · ${reservation.cancellationReason}` : ''}`} />
          )}
        </div>

        {isActive && !hasStarted && (
          <div className="stack-form">
            <label>
              Motivo de la cancelación (opcional)
              <input value={reason} maxLength={255} onChange={(event) => setReason(event.target.value)} />
            </label>
            {isMine && <small className="muted">Puedes cancelar hasta 2 horas antes del inicio.</small>}
            <div className="venue-actions">
              <button type="button" className="secondary" onClick={() => cancelMutation.mutate()} disabled={cancelMutation.isPending}>
                {cancelMutation.isPending ? 'Cancelando...' : 'Cancelar reserva'}
              </button>
            </div>
          </div>
        )}

        {/* Solo la sede registra el resultado, y solo cuando el partido ya empezó */}
        {isActive && hasStarted && !isMine && (
          <div className="venue-actions">
            <button type="button" className="primary" onClick={() => outcomeMutation.mutate('complete')} disabled={outcomeMutation.isPending}>
              Marcar completada
            </button>
            <button type="button" className="secondary" onClick={() => outcomeMutation.mutate('no-show')} disabled={outcomeMutation.isPending}>
              No asistió
            </button>
          </div>
        )}

        <ErrorBox error={cancelMutation.error ?? outcomeMutation.error} />
      </div>
    </section>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="info-chip">
      <p className="label">{label}</p>
      <strong>{value}</strong>
    </div>
  )
}
