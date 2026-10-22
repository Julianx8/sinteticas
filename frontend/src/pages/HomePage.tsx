import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch, query } from '../api/client'
import { COURT_TYPES, type CourtType, type Holiday, type PageResponse, type Reservation, type Venue } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { QueryState } from '../components/ui'
import { courtTypeLabel, formatDate, formatDateTime, todayIso } from '../lib/format'

const PAGE_SIZE = 12

export function HomePage() {
  const { isAuthenticated } = useAuth()
  const [search, setSearch] = useState('')
  const [courtType, setCourtType] = useState<CourtType | ''>('')
  const [page, setPage] = useState(0)

  const venuesQuery = useQuery({
    queryKey: ['venues', search, courtType, page],
    queryFn: () => apiFetch<PageResponse<Venue>>(`/api/v1/venues${query({ q: search.trim(), courtType, page, size: PAGE_SIZE })}`),
    placeholderData: (previous) => previous,
  })

  const venues = venuesQuery.data?.content ?? []
  const totalPages = venuesQuery.data?.totalPages ?? 0

  return (
    <div className="dashboard-shell">
      <section className="welcome-panel">
        <div className="welcome-copy">
          <p className="eyebrow warm">Canchas sintéticas en Girardot</p>
          <h1>Reserva tu cancha fácil y rápido.</h1>
          <p>Mira las sedes, elige el horario disponible y organiza tus partidos sin llamadas ni esperas.</p>

          <div className="welcome-actions">
            <a href="#sedes" className="primary cta-button">
              Ver sedes
            </a>
            {isAuthenticated ? (
              <Link to="/mis-reservas" className="secondary cta-button">
                Mis reservas
              </Link>
            ) : (
              <Link to="/register" className="secondary cta-button">
                Crear cuenta
              </Link>
            )}
          </div>
        </div>

        <div className="welcome-side">
          <NextHoliday />
          <div className="mini-stat">
            <span>Sedes</span>
            <strong>{venuesQuery.data?.totalElements ?? '—'}</strong>
            <small>{search || courtType ? 'con este filtro' : 'disponibles para reservar'}</small>
          </div>
        </div>
      </section>

      <section className="content-panel dashboard-layout" id="sedes">
        <div className="left-column">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Explorar</p>
              <h2>Sedes</h2>
            </div>
          </div>

          <div className="search-row compact filter-row">
            <input
              type="search"
              value={search}
              onChange={(event) => { setSearch(event.target.value); setPage(0) }}
              placeholder="Buscar por nombre o dirección"
              aria-label="Buscar sedes"
            />
            <select value={courtType} onChange={(event) => { setCourtType(event.target.value as CourtType | ''); setPage(0) }} aria-label="Tipo de cancha">
              <option value="">Todos los tipos</option>
              {COURT_TYPES.map((type) => <option key={type} value={type}>{courtTypeLabel(type)}</option>)}
            </select>
          </div>

          <QueryState
            isLoading={venuesQuery.isLoading}
            error={venuesQuery.error}
            isEmpty={venues.length === 0}
            loading="Cargando sedes..."
            empty="No encontramos sedes con ese filtro."
          />

          <div className="card-grid dashboard-cards">
            {venues.map((venue) => (
              <article key={venue.id} className="venue-card">
                <h3>{venue.name}</h3>
                <p className="location-line">{venue.address}</p>
                <p className="muted">{venue.neighborhood || 'Sin barrio'}</p>
                {venue.description && <p className="description">{venue.description}</p>}
                <div className="venue-footer">
                  <Link to={`/venues/${venue.id}`} className="primary action-link">
                    Ver horarios
                  </Link>
                </div>
              </article>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="pager">
              <button type="button" className="secondary small-button" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
              <span className="muted">Página {page + 1} de {totalPages}</span>
              <button type="button" className="secondary small-button" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
            </div>
          )}
        </div>

        <aside className="right-column">
          {isAuthenticated ? <UpcomingReservations /> : (
            <div className="side-panel accent-panel">
              <p className="eyebrow">¿Primera vez?</p>
              <h3>Crea tu cuenta gratis</h3>
              <p>Con una cuenta reservas en segundos, recibes la confirmación por correo y puedes cancelar desde aquí.</p>
              <Link to="/register" className="primary link-button">Crear cuenta</Link>
            </div>
          )}
        </aside>
      </section>
    </div>
  )
}

function NextHoliday() {
  const today = todayIso()
  const year = Number(today.slice(0, 4))
  const holidaysQuery = useQuery({
    queryKey: ['holidays', year],
    queryFn: async () => {
      const [current, next] = await Promise.all([
        apiFetch<Holiday[]>(`/api/v1/holidays?year=${year}`),
        apiFetch<Holiday[]>(`/api/v1/holidays?year=${year + 1}`),
      ])
      return [...current, ...next]
    },
    staleTime: Infinity,
  })
  const next = holidaysQuery.data?.find((holiday) => holiday.date >= today)

  return (
    <div className="mini-stat accent">
      <span>Próximo festivo</span>
      <strong>{next ? formatDate(next.date) : '—'}</strong>
      <small>{next ? `${next.name} · se cobra como domingo` : 'Cargando...'}</small>
    </div>
  )
}

function UpcomingReservations() {
  const reservationsQuery = useQuery({
    queryKey: ['reservations', 'upcoming'],
    queryFn: () => apiFetch<PageResponse<Reservation>>('/api/v1/reservations/mine?upcoming=true&status=CONFIRMED&size=3&sort=startAt,asc'),
  })
  const reservations = reservationsQuery.data?.content ?? []

  return (
    <div className="side-panel">
      <div className="panel-header compact-header">
        <div>
          <p className="eyebrow">Agenda</p>
          <h3>Tus próximos partidos</h3>
        </div>
      </div>
      <QueryState
        isLoading={reservationsQuery.isLoading}
        error={reservationsQuery.error}
        isEmpty={reservations.length === 0}
        loading="Cargando..."
        empty="No tienes reservas próximas."
      />
      <div className="schedule-list">
        {reservations.map((reservation) => (
          <Link key={reservation.id} to={`/reservas/${reservation.id}`} className="schedule-item">
            <div className="schedule-time">{formatDateTime(reservation.startAt)}</div>
            <div>
              <strong>{reservation.court.name}</strong>
              <small>{reservation.court.venueName}</small>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
