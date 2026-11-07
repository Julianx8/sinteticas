import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, queryClient } from '../api/client'
import type { AvailabilityResponse, AvailabilitySlot, Court, Reservation, Venue } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { CalendarPlusIcon, CheckCircleIcon, MapPinIcon, ShareIcon } from '../components/icons'
import { ErrorBox, QueryState, Sheet, usePageTitle } from '../components/ui'
import { describeError } from '../lib/errors'
import {
  DAY_SHORT, addDays, courtTypeLabel, formatHour, formatLongDate, formatMonthShort, formatPrice, formatTime, isoDayOfWeek, todayIso,
} from '../lib/format'
import { downloadIcs, mapsUrl, whatsappUrl } from '../lib/share'

// Deben coincidir con app.reservations en application.yml
const MAX_HOURS = 3
const MAX_DAYS_AHEAD = 30
const CANCELLATION_NOTICE_HOURS = 2
const REMINDER_HOURS = 3 // app.reservations.reminder-before

const SLOT_LABELS: Record<AvailabilitySlot['status'], string> = {
  AVAILABLE: 'Libre',
  BOOKED: 'Reservada',
  BLOCKED: 'Cerrada por la sede',
  PAST: 'Ya pasó',
}

/** Rango elegido: desde la hora `start` hasta `end` (exclusiva). */
type Selection = { start: number; end: number }

export function VenueDetailPage() {
  const { venueId = '' } = useParams()
  const today = todayIso()
  const [selectedDate, setSelectedDate] = useState(today)
  const [courtId, setCourtId] = useState<string | null>(null)

  const venueQuery = useQuery({
    queryKey: ['venue', venueId],
    queryFn: () => apiFetch<Venue>(`/api/v1/venues/${venueId}`),
  })
  const courtsQuery = useQuery({
    queryKey: ['courts', venueId],
    queryFn: () => apiFetch<Court[]>(`/api/v1/venues/${venueId}/courts`),
  })
  usePageTitle(venueQuery.data?.name)

  const venue = venueQuery.data
  const courts = (courtsQuery.data ?? []).filter((court) => court.active)
  const court = courts.find((candidate) => candidate.id === courtId) ?? courts[0]

  if (!venue) {
    return <QueryState isLoading={venueQuery.isLoading} error={venueQuery.error} loading="Cargando sede..." onRetry={() => venueQuery.refetch()} />
  }

  return (
    <section className="detail-page booking-page">
      <div className="detail-hero content-panel">
        <div>
          <p className="eyebrow">Sede</p>
          <h1>{venue.name}</h1>
          {venue.description && <p className="muted">{venue.description}</p>}
        </div>

        <div className="detail-address-card">
          <p>{venue.address}{venue.neighborhood ? ` · ${venue.neighborhood}` : ''}</p>
          <div className="quick-actions">
            <a href={mapsUrl(venue.address)} target="_blank" rel="noreferrer" className="secondary link-button small-button">
              <MapPinIcon size={18} /> Cómo llegar
            </a>
            {venue.phone && <a href={`tel:${venue.phone}`} className="secondary link-button small-button">Llamar · {venue.phone}</a>}
          </div>
        </div>
      </div>

      <div className="content-panel detail-panel">
        <DayStrip today={today} selected={selectedDate} onSelect={setSelectedDate} />

        <QueryState
          isLoading={courtsQuery.isLoading}
          error={courtsQuery.error}
          isEmpty={courts.length === 0}
          loading="Cargando canchas..."
          empty="Esta sede todavía no tiene canchas disponibles."
          onRetry={() => courtsQuery.refetch()}
        />

        {courts.length > 1 && (
          <div className="court-tabs" role="tablist" aria-label="Canchas">
            {courts.map((candidate) => (
              <button
                key={candidate.id}
                type="button"
                role="tab"
                aria-selected={candidate.id === court?.id}
                className={candidate.id === court?.id ? 'active' : ''}
                onClick={() => setCourtId(candidate.id)}
              >
                <strong>{candidate.name}</strong>
                <small>{courtTypeLabel(candidate.courtType)}</small>
              </button>
            ))}
          </div>
        )}

        {/* key: al cambiar de cancha o de día empieza una selección nueva */}
        {court && <CourtBooking key={`${court.id}-${selectedDate}`} venue={venue} court={court} date={selectedDate} />}
      </div>
    </section>
  )
}

/** Tira horizontal con los días que se pueden reservar (hoy a +30). */
function DayStrip({ today, selected, onSelect }: { today: string; selected: string; onSelect: (date: string) => void }) {
  const days = useMemo(() => Array.from({ length: MAX_DAYS_AHEAD + 1 }, (_, index) => addDays(today, index)), [today])
  const selectedRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    selectedRef.current?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' })
  }, [selected])

  return (
    <div className="day-strip-wrap">
      <p className="label" id="day-strip-label">Fecha del partido · {formatLongDate(selected)}</p>
      <div className="day-strip" role="group" aria-labelledby="day-strip-label">
        {days.map((day, index) => {
          const isSelected = day === selected
          const top = index === 0 ? 'Hoy' : index === 1 ? 'Mañana' : DAY_SHORT[isoDayOfWeek(day)]
          return (
            <button
              key={day}
              ref={isSelected ? selectedRef : undefined}
              type="button"
              className={`day-chip ${isSelected ? 'selected' : ''}`}
              aria-pressed={isSelected}
              aria-label={formatLongDate(day)}
              data-date={day}
              onClick={() => onSelect(day)}
            >
              <small>{top}</small>
              <strong>{Number(day.slice(8))}</strong>
              <small>{formatMonthShort(day)}</small>
            </button>
          )
        })}
      </div>
    </div>
  )
}

function CourtBooking({ venue, court, date }: { venue: Venue; court: Court; date: string }) {
  const { isAuthenticated } = useAuth()
  const [selection, setSelection] = useState<Selection | null>(null)
  const [showPast, setShowPast] = useState(false)
  const [reviewOpen, setReviewOpen] = useState(false)

  const availabilityQuery = useQuery({
    queryKey: ['availability', court.id, date],
    queryFn: () => apiFetch<AvailabilityResponse>(`/api/v1/courts/${court.id}/availability?date=${date}`),
  })
  const slots = availabilityQuery.data?.slots ?? []
  const slotAt = (hour: number) => slots.find((slot) => slot.hour === hour)
  const isFree = (hour: number) => slotAt(hour)?.status === 'AVAILABLE'

  /** Cuántas horas libres seguidas hay desde `start` (máximo 3). */
  const freeRun = (start: number) => {
    let hours = 0
    while (hours < MAX_HOURS && isFree(start + hours)) hours++
    return hours
  }

  /**
   * Primer toque: elige la hora de inicio. Segundo toque más adelante: extiende hasta esa hora,
   * solo si todas las horas del medio están libres y no pasa de 3. Tocar el inicio otra vez lo quita.
   */
  const tap = (hour: number) => {
    if (selection && hour === selection.start && selection.end - selection.start === 1) return setSelection(null)
    if (selection && hour >= selection.start && hour - selection.start < freeRun(selection.start)) {
      return setSelection({ start: selection.start, end: hour + 1 })
    }
    setSelection({ start: hour, end: hour + 1 })
  }

  const visibleSlots = showPast ? slots : slots.filter((slot) => slot.status !== 'PAST')
  const pastCount = slots.length - slots.filter((slot) => slot.status !== 'PAST').length
  const hours = selection ? selection.end - selection.start : 0
  const chosen = selection ? slots.filter((slot) => slot.hour >= selection.start && slot.hour < selection.end) : []
  const total = chosen.reduce((sum, slot) => sum + slot.price, 0)

  return (
    <div className="court-booking">
      <div className="court-booking-header">
        <div>
          <h3>{court.name}</h3>
          <p className="muted">{courtTypeLabel(court.courtType)}{court.description ? ` · ${court.description}` : ''}</p>
        </div>
      </div>

      {availabilityQuery.data?.holidayName && (
        <div className="status-box sub-box">Festivo: {availabilityQuery.data.holidayName}. Rige el horario y el precio del domingo.</div>
      )}

      <QueryState
        isLoading={availabilityQuery.isLoading}
        error={availabilityQuery.error}
        isEmpty={slots.length === 0}
        loading="Consultando horas libres..."
        empty="La cancha no abre este día."
        onRetry={() => availabilityQuery.refetch()}
      />

      {slots.length > 0 && (
        <>
          <p className="muted timeline-hint">
            {selection ? 'Toca otra hora más abajo para alargar el partido (máximo 3 horas).' : 'Toca la hora en que quieres empezar.'}
          </p>
          {pastCount > 0 && (
            <button type="button" className="ghost-button" onClick={() => setShowPast(!showPast)}>
              {showPast ? 'Ocultar horas pasadas' : `Mostrar horas pasadas (${pastCount})`}
            </button>
          )}
          {visibleSlots.length === 0 && <div className="status-box">Ya pasaron todas las horas de hoy. Elige otro día arriba.</div>}

          <ol className="timeline" aria-label={`Horas de ${court.name}`}>
            {visibleSlots.map((slot, index) => {
              const previous = visibleSlots[index - 1]
              const inRange = selection !== null && slot.hour >= selection.start && slot.hour < selection.end
              const free = slot.status === 'AVAILABLE'
              return (
                <li key={slot.hour}>
                  {/* Cambio de tarifa: se avisa una sola vez donde empieza */}
                  {free && (!previous || previous.price !== slot.price) && (
                    <p className="price-divider">Desde las {formatHour(slot.hour)}: {formatPrice(slot.price)} por hora</p>
                  )}
                  <button
                    type="button"
                    className={`slot-row status-${slot.status.toLowerCase()} ${inRange ? 'in-range' : ''}`}
                    disabled={!free}
                    aria-pressed={inRange}
                    data-hour={slot.hour}
                    onClick={() => tap(slot.hour)}
                  >
                    <span className="slot-hour">{formatHour(slot.hour)}</span>
                    <span className="slot-bar" aria-hidden="true" />
                    <span className="slot-state">{inRange ? 'Elegida' : SLOT_LABELS[slot.status]}</span>
                    <span className="slot-price">{free ? formatPrice(slot.price) : ''}</span>
                  </button>
                </li>
              )
            })}
          </ol>
        </>
      )}

      {selection && (
        <div className="booking-bar" role="region" aria-label="Resumen de la reserva">
          <div className="booking-bar-info">
            <span>{formatLongDate(date)}</span>
            <strong>{formatHour(selection.start)}–{formatHour(selection.end)} · {hours} h · {formatPrice(total)}</strong>
          </div>
          <div className="duration-stepper" aria-label="Duración">
            <button type="button" className="icon-button" aria-label="Una hora menos" disabled={hours <= 1}
              onClick={() => setSelection({ start: selection.start, end: selection.end - 1 })}>−</button>
            <button type="button" className="icon-button" aria-label="Una hora más" disabled={hours >= freeRun(selection.start)}
              onClick={() => setSelection({ start: selection.start, end: selection.end + 1 })}>+</button>
          </div>
          {isAuthenticated ? (
            <button type="button" className="primary" onClick={() => setReviewOpen(true)}>Continuar</button>
          ) : (
            <Link to="/login" state={{ from: `/venues/${venue.id}` }} className="primary link-button">Inicia sesión para reservar</Link>
          )}
        </div>
      )}

      {selection && (
        <ReviewSheet
          open={reviewOpen}
          onClose={() => setReviewOpen(false)}
          venue={venue}
          court={court}
          date={date}
          chosen={chosen}
          onBooked={() => setSelection(null)}
          onSuggest={(next) => setSelection(next)}
          slots={slots}
          refetch={async () => (await availabilityQuery.refetch()).data?.slots ?? []}
        />
      )}
    </div>
  )
}

/** Revisión antes de confirmar, y pantalla de éxito con calendario, WhatsApp y cómo llegar. */
function ReviewSheet({ open, onClose, venue, court, date, chosen, onBooked, onSuggest, slots, refetch }: {
  open: boolean
  onClose: () => void
  venue: Venue
  court: Court
  date: string
  chosen: AvailabilitySlot[]
  onBooked: () => void
  onSuggest: (selection: Selection) => void
  slots: AvailabilitySlot[]
  refetch: () => Promise<AvailabilitySlot[]>
}) {
  const [notes, setNotes] = useState('')
  const [suggestion, setSuggestion] = useState<Selection | null>(null)
  const first = chosen[0]
  const last = chosen[chosen.length - 1]
  const total = chosen.reduce((sum, slot) => sum + slot.price, 0)

  const mutation = useMutation({
    mutationFn: () => apiFetch<Reservation>('/api/v1/reservations', {
      method: 'POST',
      body: { courtId: court.id, startAt: first.startAt, durationHours: chosen.length, notes: notes.trim() || undefined },
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['availability', court.id] })
    },
    onError: async (error) => {
      // Alguien reservó o la sede bloqueó: se recargan las horas y se sugiere la más cercana con la misma duración
      if (describeError(error).action !== 'refresh-availability') return
      const fresh = await refetch()
      setSuggestion(nearestFree(fresh, first.hour, chosen.length))
    },
  })

  const close = () => {
    if (mutation.isSuccess) onBooked()
    mutation.reset()
    setSuggestion(null)
    onClose()
  }

  if (!first || !last) return null

  // Desglose por tarifa: "2 h × $90.000"
  const breakdown = chosen.reduce<{ price: number; hours: number }[]>((groups, slot) => {
    const current = groups[groups.length - 1]
    if (current && current.price === slot.price) current.hours++
    else groups.push({ price: slot.price, hours: 1 })
    return groups
  }, [])
  const cancelUntil = new Date(new Date(first.startAt).getTime() - CANCELLATION_NOTICE_HOURS * 3600_000)
  const canCancelFree = cancelUntil.getTime() > Date.now()
  const reservation = mutation.data

  if (reservation) {
    const shareText = `⚽ Partido el ${formatLongDate(date)} de ${formatTime(reservation.startAt)} a ${formatTime(reservation.endAt)} en ${venue.name} (${court.name}, ${courtTypeLabel(court.courtType)}). ${venue.address}. Cómo llegar: ${mapsUrl(venue.address)}`
    return (
      <Sheet open={open} onClose={close} title="¡Cancha reservada!">
        <div className="success-hero">
          <CheckCircleIcon size={56} />
          <p><strong>{formatLongDate(date)}</strong>, {formatTime(reservation.startAt)} a {formatTime(reservation.endAt)}</p>
          <p className="muted">{venue.name} · {court.name} · {formatPrice(reservation.totalPrice)} · pago en la sede</p>
          <p className="muted">Te enviamos el detalle por correo y te recordaremos el partido {REMINDER_HOURS} horas antes.</p>
        </div>
        <div className="share-grid">
          <button type="button" className="secondary" onClick={() => downloadIcs({
            id: reservation.id, courtName: court.name, venueName: venue.name, address: venue.address, startAt: reservation.startAt, endAt: reservation.endAt,
          })}>
            <CalendarPlusIcon size={18} /> Agregar al calendario
          </button>
          <a className="secondary link-button" href={whatsappUrl(shareText)} target="_blank" rel="noreferrer">
            <ShareIcon size={18} /> Compartir por WhatsApp
          </a>
          <a className="secondary link-button" href={mapsUrl(venue.address)} target="_blank" rel="noreferrer">
            <MapPinIcon size={18} /> Cómo llegar
          </a>
          <Link className="primary link-button" to={`/reservas/${reservation.id}`}>Ver mi reserva</Link>
        </div>
      </Sheet>
    )
  }

  return (
    <Sheet open={open} onClose={close} title="Revisa tu reserva">
      <dl className="review-list">
        <div><dt>Sede</dt><dd>{venue.name}</dd></div>
        <div><dt>Cancha</dt><dd>{court.name} · {courtTypeLabel(court.courtType)}</dd></div>
        <div><dt>Fecha</dt><dd className="capitalize">{formatLongDate(date)}</dd></div>
        <div><dt>Hora</dt><dd>{formatTime(first.startAt)} a {formatTime(last.endAt)} ({chosen.length} h)</dd></div>
        {breakdown.map((group) => (
          <div key={group.price} className="review-price-line">
            <dt>{group.hours} h × {formatPrice(group.price)}</dt><dd>{formatPrice(group.hours * group.price)}</dd>
          </div>
        ))}
        <div className="review-total"><dt>Total · pago en la sede</dt><dd>{formatPrice(total)}</dd></div>
      </dl>

      <p className={`cancel-policy ${canCancelFree ? '' : 'warning'}`}>
        {canCancelFree
          ? `Puedes cancelar desde la app hasta las ${formatTime(cancelUntil.toISOString())} (${CANCELLATION_NOTICE_HOURS} h antes).`
          : `Faltan menos de ${CANCELLATION_NOTICE_HOURS} horas: esta reserva ya no se podrá cancelar desde la app.`}
      </p>

      <label className="field">
        Nota para la sede (opcional)
        <input value={notes} maxLength={500} onChange={(event) => setNotes(event.target.value)} placeholder="Ej.: llevamos balón" />
      </label>

      <ErrorBox error={mutation.error}>
        {suggestion && (
          <button type="button" className="primary small-button" onClick={() => { onSuggest(suggestion); mutation.reset(); setSuggestion(null) }}>
            Cambiar a {formatHour(suggestion.start)}–{formatHour(suggestion.end)}
          </button>
        )}
      </ErrorBox>
      {mutation.isError && !suggestion && describeError(mutation.error).action === 'refresh-availability' && slots.length > 0 && (
        <p className="muted">No hay otra hora libre con la misma duración este día. Cierra y prueba otro día o menos horas.</p>
      )}

      <div className="sheet-actions">
        <button type="button" className="secondary" onClick={close}>Volver</button>
        <button type="button" className="primary" onClick={() => mutation.mutate()} disabled={mutation.isPending || (mutation.isError && !suggestion && describeError(mutation.error).action === 'refresh-availability')}>
          {mutation.isPending ? 'Reservando...' : 'Confirmar reserva'}
        </button>
      </div>
    </Sheet>
  )
}

/** La hora de inicio libre más cercana a `hour` que tenga `duration` horas libres seguidas. */
function nearestFree(slots: AvailabilitySlot[], hour: number, duration: number): Selection | null {
  const free = new Set(slots.filter((slot) => slot.status === 'AVAILABLE').map((slot) => slot.hour))
  const starts = slots
    .map((slot) => slot.hour)
    .filter((start) => Array.from({ length: duration }, (_, offset) => start + offset).every((candidate) => free.has(candidate)))
    .sort((a, b) => Math.abs(a - hour) - Math.abs(b - hour) || a - b)
  return starts.length ? { start: starts[0], end: starts[0] + duration } : null
}
