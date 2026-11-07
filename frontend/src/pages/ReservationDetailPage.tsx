import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, fetchWithOfflineCopy, queryClient } from '../api/client'
import type { Court, Reservation, Venue } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CalendarPlusIcon, MapPinIcon, ShareIcon } from '../components/icons'
import { ErrorBox, OfflineNotice, PageHeading, QueryState, ReservationStatusTag, useConfirm } from '../components/ui'
import { courtTypeLabel, formatCountdown, formatDateTime, formatPrice, formatTime } from '../lib/format'
import { defaultPlayers, downloadIcs, mapsUrl, splitBill, whatsappUrl } from '../lib/share'

// Debe coincidir con app.reservations.customer-cancellation-notice
const CANCELLATION_NOTICE_HOURS = 2

export function ReservationDetailPage() {
  const { reservationId = '' } = useParams()
  const { user } = useAuth()
  const confirm = useConfirm()
  const [reason, setReason] = useState('')

  const reservationQuery = useQuery({
    queryKey: ['reservations', 'detail', reservationId],
    queryFn: () => fetchWithOfflineCopy<Reservation>(`/api/v1/reservations/${reservationId}`, `reservation:${reservationId}`),
  })
  const reservation = reservationQuery.data
  // Datos públicos de la sede (teléfono) y de la cancha (tipo, para dividir la cuenta)
  const venueQuery = useQuery({
    queryKey: ['venue', reservation?.court.venueId],
    queryFn: () => apiFetch<Venue>(`/api/v1/venues/${reservation!.court.venueId}`),
    enabled: Boolean(reservation),
  })
  const courtQuery = useQuery({
    queryKey: ['court', reservation?.court.id],
    queryFn: () => apiFetch<Court>(`/api/v1/courts/${reservation!.court.id}`),
    enabled: Boolean(reservation),
  })

  const update = (updated: Reservation) => {
    queryClient.setQueryData(['reservations', 'detail', reservationId], updated)
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

  if (!reservation) {
    return <QueryState isLoading={reservationQuery.isLoading} error={reservationQuery.error} loading="Cargando reserva..." onRetry={() => reservationQuery.refetch()} />
  }

  const isMine = reservation.customer.id === user?.id
  const isActive = reservation.status === 'CONFIRMED'
  const hasStarted = new Date(reservation.startAt) <= new Date()
  const cancelUntil = new Date(new Date(reservation.startAt).getTime() - CANCELLATION_NOTICE_HOURS * 3600_000)
  // El cliente cancela hasta 2 h antes; la sede, hasta que empiece
  const canCancel = isActive && !hasStarted && (!isMine || cancelUntil > new Date())
  const phone = venueQuery.data?.phone
  const match = {
    id: reservation.id,
    courtName: reservation.court.name,
    venueName: reservation.court.venueName,
    address: reservation.court.venueAddress,
    startAt: reservation.startAt,
    endAt: reservation.endAt,
  }
  const shareText = `⚽ Partido el ${formatDateTime(reservation.startAt)} hasta las ${formatTime(reservation.endAt)} en ${reservation.court.venueName} (${reservation.court.name}). ${reservation.court.venueAddress}. Cómo llegar: ${mapsUrl(reservation.court.venueAddress)}`

  const cancel = async () => {
    const ok = await confirm({
      title: '¿Cancelar esta reserva?',
      message: isMine
        ? 'La hora quedará libre para otras personas. Esta acción no se puede deshacer.'
        : `Le llegará un correo a ${reservation.customer.fullName} avisándole.`,
      confirmLabel: 'Cancelar reserva',
      danger: true,
    })
    if (ok) cancelMutation.mutate()
  }

  return (
    <section className="content-panel detail-page">
      <PageHeading eyebrow="Reserva" title={`${reservation.court.name} · ${reservation.court.venueName}`}>
        <Link to={isMine ? '/mis-reservas' : `/admin/sedes/${reservation.court.venueId}/agenda`} className="secondary link-button">Volver</Link>
      </PageHeading>

      <OfflineNotice savedAt={reservation.offlineSavedAt} />
      <div className="profile-card profile-layout">
        <div className="role-list">
          <ReservationStatusTag status={reservation.status} />
          {isActive && !hasStarted && <span className="countdown-pill">Empieza {formatCountdown(reservation.startAt)}</span>}
        </div>

        <div className="info-grid">
          <Info label="Inicio" value={formatDateTime(reservation.startAt)} />
          <Info label="Fin" value={formatDateTime(reservation.endAt)} />
          <Info label="Duración" value={`${reservation.durationHours} hora${reservation.durationHours > 1 ? 's' : ''}`} />
          <Info label="Total · pago en la sede" value={formatPrice(reservation.totalPrice)} />
          <Info label="Dirección" value={reservation.court.venueAddress} />
          {!isMine && <Info label="Cliente" value={`${reservation.customer.fullName} · ${reservation.customer.email}`} />}
          {reservation.notes && <Info label="Notas" value={reservation.notes} />}
          {reservation.cancelledAt && (
            <Info label="Cancelada" value={`${formatDateTime(reservation.cancelledAt)}${reservation.cancellationReason ? ` · ${reservation.cancellationReason}` : ''}`} />
          )}
        </div>

        {isActive && !hasStarted && (
          <div className="share-grid">
            <button type="button" className="secondary" onClick={() => downloadIcs(match)}><CalendarPlusIcon size={18} /> Agregar al calendario</button>
            <a className="secondary link-button" href={whatsappUrl(shareText)} target="_blank" rel="noreferrer"><ShareIcon size={18} /> Compartir por WhatsApp</a>
            <a className="secondary link-button" href={mapsUrl(reservation.court.venueAddress)} target="_blank" rel="noreferrer"><MapPinIcon size={18} /> Cómo llegar</a>
          </div>
        )}
      </div>

      {isActive && isMine && (
        <SplitBill total={reservation.totalPrice} courtType={courtQuery.data?.courtType} reservation={reservation} />
      )}

      {canCancel && (
        <div className="admin-card">
          <h3>Cancelar</h3>
          {isMine && <p className="muted">Puedes cancelar desde la app hasta las {formatTime(cancelUntil.toISOString())} ({CANCELLATION_NOTICE_HOURS} h antes del partido).</p>}
          <div className="stack-form">
            <label>
              Motivo de la cancelación (opcional)
              <input value={reason} maxLength={255} onChange={(event) => setReason(event.target.value)} />
            </label>
            <div className="venue-actions">
              <button type="button" className="danger-button" onClick={cancel} disabled={cancelMutation.isPending}>
                {cancelMutation.isPending ? 'Cancelando...' : 'Cancelar reserva'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Muy tarde para cancelar desde la app: se explica en vez de mostrar un botón que fallaría */}
      {isActive && isMine && !hasStarted && !canCancel && (
        <div className="status-box sub-box">
          Ya no se puede cancelar desde la app (el plazo era hasta las {formatTime(cancelUntil.toISOString())}).
          {phone ? <> Si no puedes ir, avísale a la sede: <a href={`tel:${phone}`}>{phone}</a>.</> : ' Si no puedes ir, avísale a la sede.'}
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
    </section>
  )
}

/** Cuánto pone cada jugador. Todo se calcula aquí; no se guarda en el backend. */
function SplitBill({ total, courtType, reservation }: { total: number; courtType?: string; reservation: Reservation }) {
  const [players, setPlayers] = useState<number | null>(null)
  const count = players ?? defaultPlayers(courtType)
  const { perPlayer, leftover } = splitBill(total, count)
  const message = `⚽ Partido el ${formatDateTime(reservation.startAt)} en ${reservation.court.venueName}. `
    + `Total ${formatPrice(total)} entre ${count}: toca a ${formatPrice(perPlayer)} cada uno.`

  return (
    <div className="admin-card split-bill">
      <h3>¿Cuánto pone cada uno?</h3>
      <div className="split-row">
        <span className="muted">Jugadores{courtType ? ` (${courtTypeLabel(courtType as Court['courtType'])})` : ''}</span>
        <div className="duration-stepper">
          <button type="button" className="icon-button" aria-label="Un jugador menos" disabled={count <= 2} onClick={() => setPlayers(count - 1)}>−</button>
          <output aria-live="polite" aria-label="Número de jugadores">{count}</output>
          <button type="button" className="icon-button" aria-label="Un jugador más" disabled={count >= 30} onClick={() => setPlayers(count + 1)}>+</button>
        </div>
      </div>
      <p className="split-amount"><strong data-testid="per-player">{formatPrice(perPlayer)}</strong> por jugador</p>
      {leftover > 0 && <p className="muted">Redondeado a la centena: sobran {formatPrice(leftover)}.</p>}
      <a className="secondary link-button" href={whatsappUrl(message)} target="_blank" rel="noreferrer"><ShareIcon size={18} /> Enviar al grupo</a>
    </div>
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
