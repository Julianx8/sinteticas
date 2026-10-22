import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { apiFetch } from '../../api/client'
import { COURT_TYPES, type Court, type CourtInput, type Venue, type VenueInput } from '../../api/types'
import { ErrorBox, SuccessBox } from '../../components/ui'
import { courtTypeLabel } from '../../lib/format'

const EMPTY_VENUE: VenueInput = { name: '', address: '', neighborhood: '', phone: '', description: '', active: true }
const EMPTY_COURT: CourtInput = { name: '', courtType: 'FUTBOL_5', description: '', active: true }

function venueInput(venue?: Venue): VenueInput {
  if (!venue) return EMPTY_VENUE
  return {
    name: venue.name,
    address: venue.address,
    neighborhood: venue.neighborhood ?? '',
    phone: venue.phone ?? '',
    description: venue.description ?? '',
    active: venue.active,
  }
}

/** Crea una sede o, si recibe {@code venue}, la edita. */
export function VenueForm({ venue, onSaved }: { venue?: Venue; onSaved: (venue: Venue) => void }) {
  const [form, setForm] = useState<VenueInput>(() => venueInput(venue))
  const mutation = useMutation({
    mutationFn: () => apiFetch<Venue>(venue ? `/api/v1/venues/${venue.id}` : '/api/v1/venues', {
      method: venue ? 'PUT' : 'POST',
      body: form,
    }),
    onSuccess: (saved) => {
      if (!venue) setForm(EMPTY_VENUE)
      onSaved(saved)
    },
  })
  const set = (field: keyof VenueInput) => (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    mutation.reset()
    setForm((current) => ({ ...current, [field]: event.target.value }))
  }

  return (
    <form className="stack-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}>
      <label>
        Nombre
        <input value={form.name} onChange={set('name')} maxLength={150} required />
      </label>
      <label>
        Dirección
        <input value={form.address} onChange={set('address')} maxLength={255} required />
      </label>
      <label>
        Barrio
        <input value={form.neighborhood} onChange={set('neighborhood')} maxLength={100} />
      </label>
      <label>
        Teléfono
        <input type="tel" value={form.phone} onChange={set('phone')} maxLength={30} pattern="[0-9 +()\-]*" title="Solo números, espacios y + ( ) -" />
      </label>
      <label>
        Descripción
        <textarea value={form.description} onChange={set('description')} rows={3} maxLength={2000} />
      </label>
      <label className="inline-toggle">
        <input type="checkbox" checked={form.active} onChange={(event) => { mutation.reset(); setForm((current) => ({ ...current, active: event.target.checked })) }} />
        Sede activa (visible para reservar)
      </label>

      <ErrorBox error={mutation.error} />
      <SuccessBox show={mutation.isSuccess}>{venue ? 'Cambios guardados.' : 'Sede creada.'}</SuccessBox>

      <button type="submit" className="primary" disabled={mutation.isPending}>
        {mutation.isPending ? 'Guardando...' : venue ? 'Guardar cambios' : 'Crear sede'}
      </button>
    </form>
  )
}

/** Crea una cancha en la sede o, si recibe {@code court}, la edita. */
export function CourtForm({ venueId, court, onSaved, onCancel }: {
  venueId: string
  court?: Court
  onSaved: (court: Court) => void
  onCancel?: () => void
}) {
  const [form, setForm] = useState<CourtInput>(() => court
    ? { name: court.name, courtType: court.courtType, description: court.description ?? '', active: court.active }
    : EMPTY_COURT)
  const mutation = useMutation({
    mutationFn: () => apiFetch<Court>(court ? `/api/v1/courts/${court.id}` : `/api/v1/venues/${venueId}/courts`, {
      method: court ? 'PUT' : 'POST',
      body: form,
    }),
    onSuccess: (saved) => {
      if (!court) setForm(EMPTY_COURT)
      onSaved(saved)
    },
  })

  return (
    <form className="stack-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}>
      <label>
        Nombre de la cancha
        <input value={form.name} maxLength={100} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} required />
      </label>
      <label>
        Tipo
        <select value={form.courtType} onChange={(event) => setForm((current) => ({ ...current, courtType: event.target.value as CourtInput['courtType'] }))}>
          {COURT_TYPES.map((type) => <option key={type} value={type}>{courtTypeLabel(type)}</option>)}
        </select>
      </label>
      <label>
        Descripción
        <textarea value={form.description} rows={2} maxLength={2000} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
      </label>
      <label className="inline-toggle">
        <input type="checkbox" checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} />
        Cancha activa
      </label>

      <ErrorBox error={mutation.error} />
      {!court && <SuccessBox show={mutation.isSuccess}>Cancha creada. Ahora configura su horario.</SuccessBox>}

      <div className="venue-actions">
        <button type="submit" className="primary" disabled={mutation.isPending}>
          {mutation.isPending ? 'Guardando...' : court ? 'Guardar' : 'Crear cancha'}
        </button>
        {onCancel && <button type="button" className="secondary" onClick={onCancel}>Cancelar</button>}
      </div>
    </form>
  )
}
