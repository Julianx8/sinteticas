import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch, query } from '../api/client'
import type { PageResponse, Reservation } from '../api/types'
import { PageHeading, QueryState, ReservationStatusTag } from '../components/ui'
import { formatDateTime, formatPrice, formatTime } from '../lib/format'

type Tab = 'upcoming' | 'history'

export function ReservationsPage() {
  const [tab, setTab] = useState<Tab>('upcoming')
  const [page, setPage] = useState(0)

  const reservationsQuery = useQuery({
    queryKey: ['reservations', tab, page],
    queryFn: () => apiFetch<PageResponse<Reservation>>(`/api/v1/reservations/mine${query(
      tab === 'upcoming'
        ? { upcoming: true, status: 'CONFIRMED', sort: 'startAt,asc', page, size: 10 }
        : { sort: 'startAt,desc', page, size: 10 },
    )}`),
    placeholderData: (previous) => previous,
  })

  const reservations = reservationsQuery.data?.content ?? []
  const totalPages = reservationsQuery.data?.totalPages ?? 0
  const changeTab = (next: Tab) => {
    setTab(next)
    setPage(0)
  }

  return (
    <section className="content-panel">
      <PageHeading eyebrow="Mi cuenta" title="Mis reservas">
        <Link to="/" className="primary link-button">Reservar</Link>
      </PageHeading>

      <div className="tabs" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'upcoming'} className={tab === 'upcoming' ? 'active' : ''} onClick={() => changeTab('upcoming')}>Próximas</button>
        <button type="button" role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'active' : ''} onClick={() => changeTab('history')}>Todas</button>
      </div>

      <QueryState
        isLoading={reservationsQuery.isLoading}
        error={reservationsQuery.error}
        isEmpty={reservations.length === 0}
        loading="Cargando reservas..."
        empty={tab === 'upcoming' ? 'No tienes partidos próximos.' : 'Aún no tienes reservas.'}
      />

      <div className="reservation-stack">
        {reservations.map((reservation) => (
          <Link key={reservation.id} to={`/reservas/${reservation.id}`} className="reservation-card">
            <div className="reservation-topline">
              <ReservationStatusTag status={reservation.status} />
              <strong>{formatPrice(reservation.totalPrice)}</strong>
            </div>
            <h3>{reservation.court.name}</h3>
            <p>{reservation.court.venueName}</p>
            <p>{formatDateTime(reservation.startAt)} – {formatTime(reservation.endAt)}</p>
          </Link>
        ))}
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
