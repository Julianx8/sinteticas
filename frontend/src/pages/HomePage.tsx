import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch, fetchWithOfflineCopy, query } from '../api/client'
import { COURT_TYPES, type CourtType, type Holiday, type PageResponse, type Reservation, type Venue } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { MapPinIcon, ShareIcon } from '../components/icons'
import { QueryState, usePageTitle } from '../components/ui'
import {
  DAY_SHORT, addDays, courtTypeLabel, currentHourInBogota, formatCountdown, formatDate, formatDateTime, formatHour, formatLongDate,
  formatPrice, formatTime, isoDayOfWeek, todayIso,
} from '../lib/format'
import { mapsUrl, whatsappUrl } from '../lib/share'

const PAGE_SIZE = 12

export function HomePage() {
  const { isAuthenticated } = useAuth()
  usePageTitle(undefined)
  const [search, setSearch] = useState('')
  const [courtType, setCourtType] = useState<CourtType | ''>('')
  const [page, setPage] = useState(0)
  // "¿Cuándo quieres jugar?": con día y hora, solo salen sedes con alguna cancha libre a esa hora
  const [when, setWhen] = useState<When | null>(null)

  const venuesQuery = useQuery({
    queryKey: ['venues', search, courtType, page, when?.date, when?.hour],
    queryFn: () => apiFetch<PageResponse<Venue>>(`/api/v1/venues${query({
      q: search.trim(), courtType, page, size: PAGE_SIZE, date: when?.date, hour: when?.hour,
    })}`),
    placeholderData: (previous) => previous,
  })
  const changeWhen = (next: When | null) => {
    setWhen(next)
    setPage(0)
  }

  const venues = venuesQuery.data?.content ?? []
  const totalPages = venuesQuery.data?.totalPages ?? 0

  return (
    <div className="dashboard-shell">
      {isAuthenticated && <NextMatchCard />}
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
            <small>{search || courtType || when ? 'con este filtro' : 'disponibles para reservar'}</small>
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

          <WhenPicker value={when} onChange={changeWhen} />

          <div className="search-row compact filter-row">
            <input
              type="search"
              value={search}
              onChange={(event) => { setSearch(event.target.value); setPage(0) }}
              placeholder="Buscar por nombre o dirección"
              aria-label="Buscar sedes"
            />
          </div>
          <div className="chip-row" role="group" aria-label="Tipo de cancha">
            {(['', ...COURT_TYPES] as const).map((type) => (
              <button
                key={type || 'todos'}
                type="button"
                className={`filter-chip ${courtType === type ? 'selected' : ''}`}
                aria-pressed={courtType === type}
                onClick={() => { setCourtType(type as CourtType | ''); setPage(0) }}
              >
                {type ? courtTypeLabel(type) : 'Todas'}
              </button>
            ))}
          </div>

          <QueryState
            isLoading={venuesQuery.isLoading}
            error={venuesQuery.error}
            isEmpty={venues.length === 0 && !when}
            loading="Cargando sedes..."
            empty="No encontramos sedes con ese filtro."
            onRetry={() => venuesQuery.refetch()}
          />
          {/* Sin resultados para esa hora: se ofrecen las horas vecinas en vez de un "no hay" seco */}
          {when && !venuesQuery.isLoading && !venuesQuery.error && venues.length === 0 && (
            <div className="status-box empty-suggest">
              <p>No hay canchas libres el {formatLongDate(when.date)} a las {formatHour(when.hour)}{courtType ? ` (${courtTypeLabel(courtType)})` : ''}.</p>
              <div className="chip-row">
                {[when.hour - 1, when.hour + 1, when.hour + 2].filter((hour) => hourOptions(when.date).includes(hour)).map((hour) => (
                  <button key={hour} type="button" className="filter-chip" onClick={() => changeWhen({ ...when, hour })}>
                    Probar a las {formatHour(hour)}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="card-grid dashboard-cards">
            {venues.map((venue) => (
              <article key={venue.id} className="venue-card">
                <h3>{venue.name}</h3>
                <p className="location-line">{venue.address}</p>
                <p className="muted">{venue.neighborhood || 'Sin barrio'}</p>
                {venue.courtTypes.length > 0 && (
                  <p className="venue-types">{venue.courtTypes.map(courtTypeLabel).join(' · ')}</p>
                )}
                {venue.minPricePerHour !== undefined && (
                  <p className="venue-price">Desde <strong>{formatPrice(venue.minPricePerHour)}</strong> por hora</p>
                )}
                {venue.description && <p className="description">{venue.description}</p>}
                <div className="venue-footer">
                  {when ? (
                    <Link to={`/venues/${venue.id}?fecha=${when.date}&hora=${when.hour}`} className="primary action-link">
                      Reservar a las {formatHour(when.hour)}
                    </Link>
                  ) : (
                    <Link to={`/venues/${venue.id}`} className="primary action-link">
                      Ver horarios
                    </Link>
                  )}
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

type When = { date: string; hour: number }

/** Horas que tiene sentido buscar: de 6 a 22, y si es hoy, solo las que aún no empiezan. */
function hourOptions(date: string): number[] {
  const from = date === todayIso() ? Math.max(6, currentHourInBogota() + 1) : 6
  return Array.from({ length: Math.max(0, 23 - from) }, (_, index) => from + index)
}

/** Hoy, mañana y el próximo fin de semana: los días en que más se juega. */
function dayOptions(): { date: string; label: string }[] {
  const today = todayIso()
  const days = [{ date: today, label: 'Hoy' }, { date: addDays(today, 1), label: 'Mañana' }]
  for (let offset = 2; offset <= 7; offset++) {
    const date = addDays(today, offset)
    const dayOfWeek = isoDayOfWeek(date)
    if (dayOfWeek === 6 || dayOfWeek === 7) days.push({ date, label: `${DAY_SHORT[dayOfWeek]} ${Number(date.slice(8))}` })
  }
  return days.filter((day) => hourOptions(day.date).length > 0)
}

function WhenPicker({ value, onChange }: { value: When | null; onChange: (value: When | null) => void }) {
  const days = dayOptions()
  const pickDay = (date: string) => {
    const hours = hourOptions(date)
    // Conserva la hora elegida si sirve ese día; si no, la noche (19:00) o la primera disponible
    const hour = value && hours.includes(value.hour) ? value.hour : hours.includes(19) ? 19 : hours[0]
    onChange({ date, hour })
  }

  return (
    <div className="when-picker">
      <p className="label" id="when-label">¿Cuándo quieres jugar?</p>
      <div className="chip-row" role="group" aria-labelledby="when-label">
        <button type="button" className={`filter-chip ${value ? '' : 'selected'}`} aria-pressed={!value} onClick={() => onChange(null)}>
          Cualquier día
        </button>
        {days.map((day) => (
          <button
            key={day.date}
            type="button"
            className={`filter-chip ${value?.date === day.date ? 'selected' : ''}`}
            aria-pressed={value?.date === day.date}
            onClick={() => pickDay(day.date)}
          >
            {day.label}
          </button>
        ))}
      </div>
      {value && (
        <label className="when-hour">
          Hora
          <select value={value.hour} onChange={(event) => onChange({ ...value, hour: Number(event.target.value) })}>
            {hourOptions(value.date).map((hour) => <option key={hour} value={hour}>{formatHour(hour)}</option>)}
          </select>
          <span className="muted">Solo sedes con cancha libre {formatLongDate(value.date)} de {formatHour(value.hour)} a {formatHour(value.hour + 1)}</span>
        </label>
      )}
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
    queryFn: upcomingReservations,
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

const upcomingReservations = () => fetchWithOfflineCopy<PageResponse<Reservation>>(
  '/api/v1/reservations/mine?upcoming=true&status=CONFIRMED&size=3&sort=startAt,asc', 'upcoming',
)

/** Lo primero que ve un jugador con partido pendiente: cuándo, dónde y cómo llegar. */
function NextMatchCard() {
  const upcomingQuery = useQuery({ queryKey: ['reservations', 'upcoming'], queryFn: upcomingReservations })
  const next = upcomingQuery.data?.content[0]
  if (!next) return null
  const shareText = `⚽ Partido el ${formatDateTime(next.startAt)} en ${next.court.venueName} (${next.court.name}). ${next.court.venueAddress}`

  return (
    <section className="next-match" aria-label="Tu próximo partido">
      <div className="next-match-info">
        <p className="eyebrow">Tu próximo partido · {formatCountdown(next.startAt)}</p>
        <h2>{formatDateTime(next.startAt)} – {formatTime(next.endAt)}</h2>
        <p>{next.court.venueName} · {next.court.name}</p>
      </div>
      <div className="next-match-actions">
        <a className="secondary link-button small-button" href={mapsUrl(next.court.venueAddress)} target="_blank" rel="noreferrer"><MapPinIcon size={18} /> Cómo llegar</a>
        <a className="secondary link-button small-button" href={whatsappUrl(shareText)} target="_blank" rel="noreferrer"><ShareIcon size={18} /> Compartir</a>
        <Link className="primary link-button small-button" to={`/reservas/${next.id}`}>Ver reserva</Link>
      </div>
    </section>
  )
}
