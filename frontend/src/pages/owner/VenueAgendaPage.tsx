import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, query, queryClient } from '../../api/client'
import type { Court, PageResponse, Reservation, ReservationStatus, Venue } from '../../api/types'
import { DateRange, ErrorBox, PageHeading, QueryState, ReservationStatusTag } from '../../components/ui'
import { STATUS_LABELS, addDays, formatDate, formatPrice, formatTime, todayIso } from '../../lib/format'

const PAGE_SIZE = 50

export function VenueAgendaPage() {
  const { venueId = '' } = useParams()
  const today = todayIso()
  const [range, setRange] = useState({ from: today, to: addDays(today, 7) })
  const [status, setStatus] = useState<ReservationStatus | ''>('')
  const [courtId, setCourtId] = useState('')
  const [page, setPage] = useState(0)

  const venueQuery = useQuery({ queryKey: ['venue', venueId], queryFn: () => apiFetch<Venue>(`/api/v1/venues/${venueId}`) })
  const courtsQuery = useQuery({ queryKey: ['courts', venueId], queryFn: () => apiFetch<Court[]>(`/api/v1/venues/${venueId}/courts`) })
  const agendaQuery = useQuery({
    queryKey: ['venue-agenda', venueId, range.from, range.to, status, courtId, page],
    queryFn: () => apiFetch<PageResponse<Reservation>>(`/api/v1/venues/${venueId}/reservations${query({
      from: range.from, to: range.to, status, courtId, page, size: PAGE_SIZE, sort: 'startAt,asc',
    })}`),
    placeholderData: (previous) => previous,
  })

  const outcomeMutation = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'complete' | 'no-show' | 'cancel' }) =>
      apiFetch<Reservation>(`/api/v1/reservations/${id}/${action}`, {
        method: 'POST',
        body: action === 'cancel' ? { reason: 'Cancelada por la sede' } : undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['venue-agenda', venueId] })
      queryClient.invalidateQueries({ queryKey: ['availability'] })
      queryClient.invalidateQueries({ queryKey: ['venue-reports'] })
    },
  })

  const reservations = agendaQuery.data?.content ?? []
  const totalPages = agendaQuery.data?.totalPages ?? 0
  const byDay = groupByDay(reservations)
  const resetPage = <T,>(setter: (value: T) => void) => (value: T) => {
    setter(value)
    setPage(0)
  }

  return (
    <section className="content-panel routes-page">
      <PageHeading eyebrow="Agenda" title={venueQuery.data?.name ?? 'Sede'}>
        <Link to={`/admin/sedes/${venueId}`} className="secondary link-button">Gestionar sede</Link>
      </PageHeading>

      <div className="filter-row">
        <DateRange from={range.from} to={range.to} onChange={(from, to) => { setRange({ from, to }); setPage(0) }} />
        <label>
          Estado
          <select value={status} onChange={(event) => resetPage(setStatus)(event.target.value as ReservationStatus | '')}>
            <option value="">Todos</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
        <label>
          Cancha
          <select value={courtId} onChange={(event) => resetPage(setCourtId)(event.target.value)}>
            <option value="">Todas</option>
            {(courtsQuery.data ?? []).map((court) => <option key={court.id} value={court.id}>{court.name}</option>)}
          </select>
        </label>
      </div>

      <QueryState
        isLoading={agendaQuery.isLoading}
        error={agendaQuery.error}
        isEmpty={reservations.length === 0}
        loading="Cargando agenda..."
        empty="No hay reservas con estos filtros."
      />
      <ErrorBox error={outcomeMutation.error} />

      {byDay.map(([day, dayReservations]) => (
        <div key={day} className="agenda-day">
          <h3>{formatDate(day)}</h3>
          <div className="mini-list">
            {dayReservations.map((reservation) => {
              const started = new Date(reservation.startAt) <= new Date()
              const active = reservation.status === 'CONFIRMED'
              const pending = outcomeMutation.isPending && outcomeMutation.variables?.id === reservation.id
              return (
                <div key={reservation.id} className="mini-item">
                  <div>
                    <strong>{formatTime(reservation.startAt)} – {formatTime(reservation.endAt)} · {reservation.court.name}</strong>
                    <p>{reservation.customer.fullName} · {reservation.customer.email}</p>
                    <p className="muted">{formatPrice(reservation.totalPrice)}{reservation.notes ? ` · “${reservation.notes}”` : ''}</p>
                    <ReservationStatusTag status={reservation.status} />
                  </div>
                  <div className="user-actions">
                    {active && started && (
                      <>
                        <button type="button" className="primary small-button" disabled={pending} onClick={() => outcomeMutation.mutate({ id: reservation.id, action: 'complete' })}>Completada</button>
                        <button type="button" className="secondary small-button" disabled={pending} onClick={() => outcomeMutation.mutate({ id: reservation.id, action: 'no-show' })}>No asistió</button>
                      </>
                    )}
                    {active && !started && (
                      <button
                        type="button"
                        className="secondary small-button"
                        disabled={pending}
                        onClick={() => window.confirm(`¿Cancelar la reserva de ${reservation.customer.fullName}? Le llegará un correo.`)
                          && outcomeMutation.mutate({ id: reservation.id, action: 'cancel' })}
                      >
                        Cancelar
                      </button>
                    )}
                    <Link to={`/reservas/${reservation.id}`} className="ghost-button">Detalle</Link>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      ))}

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

/** Agrupa por fecha de Colombia ("YYYY-MM-DD"), conservando el orden. */
function groupByDay(reservations: Reservation[]): [string, Reservation[]][] {
  const groups = new Map<string, Reservation[]>()
  for (const reservation of reservations) {
    const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date(reservation.startAt))
    groups.set(day, [...(groups.get(day) ?? []), reservation])
  }
  return [...groups.entries()]
}
