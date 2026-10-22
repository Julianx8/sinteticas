import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, queryClient } from '../api/client'
import type { AvailabilityResponse, AvailabilitySlot, Court, Reservation, Venue } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ErrorBox, QueryState } from '../components/ui'
import { addDays, courtTypeLabel, formatDate, formatHour, formatPrice, todayIso } from '../lib/format'

// Deben coincidir con app.reservations en application.yml
const MAX_HOURS = 3
const MAX_DAYS_AHEAD = 30

const SLOT_LABELS: Record<AvailabilitySlot['status'], string> = {
  AVAILABLE: 'Libre',
  BOOKED: 'Reservada',
  BLOCKED: 'No disponible',
  PAST: 'Ya pasó',
}

export function VenueDetailPage() {
  const { venueId = '' } = useParams()
  const today = todayIso()
  const [selectedDate, setSelectedDate] = useState(today)

  const venueQuery = useQuery({
    queryKey: ['venue', venueId],
    queryFn: () => apiFetch<Venue>(`/api/v1/venues/${venueId}`),
  })
  const courtsQuery = useQuery({
    queryKey: ['courts', venueId],
    queryFn: () => apiFetch<Court[]>(`/api/v1/venues/${venueId}/courts`),
  })

  const venue = venueQuery.data
  if (!venue) {
    return <QueryState isLoading={venueQuery.isLoading} error={venueQuery.error} loading="Cargando sede..." />
  }

  const courts = (courtsQuery.data ?? []).filter((court) => court.active)

  return (
    <section className="detail-page">
      <div className="detail-hero content-panel">
        <div>
          <p className="eyebrow">Sede</p>
          <h1>{venue.name}</h1>
          {venue.description && <p className="muted">{venue.description}</p>}
        </div>

        <div className="detail-address-card">
          <p>{venue.address}</p>
          <p>{venue.neighborhood || 'Barrio no informado'}</p>
          <p>{venue.phone ? <a href={`tel:${venue.phone}`}>{venue.phone}</a> : 'Sin teléfono'}</p>
        </div>
      </div>

      <div className="content-panel detail-panel">
        <div className="date-picker-wrap">
          <label htmlFor="select-date">Fecha del partido</label>
          <input
            id="select-date"
            type="date"
            value={selectedDate}
            min={today}
            max={addDays(today, MAX_DAYS_AHEAD)}
            onChange={(event) => event.target.value && setSelectedDate(event.target.value)}
          />
          <small className="muted">{formatDate(selectedDate)} · puedes reservar hasta {MAX_DAYS_AHEAD} días adelante</small>
        </div>

        <QueryState
          isLoading={courtsQuery.isLoading}
          error={courtsQuery.error}
          isEmpty={courts.length === 0}
          loading="Cargando canchas..."
          empty="Esta sede todavía no tiene canchas disponibles."
        />

        <div className="court-stack">
          {courts.map((court) => (
            <CourtAvailabilityCard key={court.id} court={court} selectedDate={selectedDate} />
          ))}
        </div>
      </div>
    </section>
  )
}

function CourtAvailabilityCard({ court, selectedDate }: { court: Court; selectedDate: string }) {
  const { isAuthenticated } = useAuth()
  const [selectedHour, setSelectedHour] = useState<number | null>(null)
  const [durationHours, setDurationHours] = useState(1)
  const [notes, setNotes] = useState('')

  const availabilityQuery = useQuery({
    queryKey: ['availability', court.id, selectedDate],
    queryFn: () => apiFetch<AvailabilityResponse>(`/api/v1/courts/${court.id}/availability?date=${selectedDate}`),
  })
  const slots = availabilityQuery.data?.slots ?? []
  const selectedSlot = slots.find((slot) => slot.hour === selectedHour) ?? null

  const mutation = useMutation({
    mutationFn: () => apiFetch<Reservation>('/api/v1/reservations', {
      method: 'POST',
      body: { courtId: court.id, startAt: selectedSlot?.startAt, durationHours, notes: notes.trim() || undefined },
    }),
    onSuccess: () => {
      setSelectedHour(null)
      setNotes('')
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['availability', court.id] })
    },
  })

  // Duraciones posibles: horas libres seguidas desde la elegida (sin pasar el máximo)
  const maxDuration = (() => {
    if (selectedHour === null) return 1
    let hours = 0
    while (hours < MAX_HOURS) {
      const slot = slots.find((candidate) => candidate.hour === selectedHour + hours)
      if (!slot || slot.status !== 'AVAILABLE') break
      hours++
    }
    return Math.max(hours, 1)
  })()
  const total = slots
    .filter((slot) => selectedHour !== null && slot.hour >= selectedHour && slot.hour < selectedHour + durationHours)
    .reduce((sum, slot) => sum + slot.price, 0)

  const selectHour = (hour: number) => {
    setSelectedHour(hour)
    setDurationHours(1)
    mutation.reset()
  }

  return (
    <article className="court-card court-card-modern">
      <div className="court-header">
        <div>
          <h3>{court.name}</h3>
          <p>{courtTypeLabel(court.courtType)}</p>
        </div>
      </div>
      {court.description && <p className="muted">{court.description}</p>}

      {availabilityQuery.data?.holidayName && (
        <div className="status-box sub-box">Festivo: {availabilityQuery.data.holidayName}. Rige el horario del domingo.</div>
      )}

      <QueryState
        isLoading={availabilityQuery.isLoading}
        error={availabilityQuery.error}
        isEmpty={slots.length === 0}
        loading="Consultando disponibilidad..."
        empty="La cancha no abre este día."
      />

      {slots.length > 0 && (
        <div className="availability-list">
          {slots.map((slot) => (
            <button
              key={slot.hour}
              type="button"
              className={`time-chip ${selectedHour === slot.hour ? 'selected' : ''} ${slot.status !== 'AVAILABLE' ? 'unavailable' : ''}`}
              disabled={slot.status !== 'AVAILABLE'}
              title={SLOT_LABELS[slot.status]}
              onClick={() => selectHour(slot.hour)}
            >
              {formatHour(slot.hour)} · {slot.status === 'AVAILABLE' ? formatPrice(slot.price) : SLOT_LABELS[slot.status]}
            </button>
          ))}
        </div>
      )}

      {mutation.isSuccess && (
        <div className="status-box success sub-box">
          Reserva confirmada. Te enviamos el detalle por correo. <Link to={`/reservas/${mutation.data.id}`}>Ver reserva</Link>
        </div>
      )}

      {selectedSlot && (
        isAuthenticated ? (
          <div className="booking-form">
            <div className="booking-summary">
              <span>{formatDate(selectedDate)} · {formatHour(selectedSlot.hour)} a {formatHour(selectedSlot.hour + durationHours)}</span>
              <strong>{formatPrice(total)}</strong>
            </div>

            <label htmlFor={`duration-${court.id}`}>Duración</label>
            <select id={`duration-${court.id}`} value={durationHours} onChange={(event) => setDurationHours(Number(event.target.value))}>
              {Array.from({ length: maxDuration }, (_, index) => index + 1).map((hours) => (
                <option key={hours} value={hours}>
                  {hours} hora{hours > 1 ? 's' : ''}
                </option>
              ))}
            </select>

            <label htmlFor={`notes-${court.id}`}>Nota para la sede (opcional)</label>
            <input id={`notes-${court.id}`} value={notes} maxLength={500} onChange={(event) => setNotes(event.target.value)} placeholder="Ej.: llevamos balón" />

            <button type="button" className="primary" onClick={() => mutation.mutate()} disabled={mutation.isPending}>
              {mutation.isPending ? 'Reservando...' : 'Confirmar reserva'}
            </button>
            <ErrorBox error={mutation.error} />
          </div>
        ) : (
          <div className="status-box sub-box">
            <Link to="/login" state={{ from: `/venues/${court.venueId}` }}>Inicia sesión</Link> para reservar esta hora.
          </div>
        )
      )}
    </article>
  )
}
