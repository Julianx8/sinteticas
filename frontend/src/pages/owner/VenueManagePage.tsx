import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, queryClient } from '../../api/client'
import type { Court, Venue } from '../../api/types'
import { ActiveTag, PageHeading, QueryState } from '../../components/ui'
import { courtTypeLabel } from '../../lib/format'
import { CourtForm, VenueForm } from './forms'

export function VenueManagePage() {
  const { venueId = '' } = useParams()
  const [editingCourtId, setEditingCourtId] = useState<string | null>(null)

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
  const courts = courtsQuery.data ?? []

  const courtSaved = () => {
    setEditingCourtId(null)
    queryClient.invalidateQueries({ queryKey: ['courts', venueId] })
  }

  return (
    <section className="content-panel routes-page">
      <PageHeading eyebrow="Gestionar sede" title={venue.name}>
        <Link to={`/admin/sedes/${venueId}/agenda`} className="secondary link-button">Agenda</Link>
        <Link to={`/admin/sedes/${venueId}/reportes`} className="secondary link-button">Reportes</Link>
        <Link to={`/venues/${venueId}`} className="secondary link-button">Ver como cliente</Link>
      </PageHeading>

      <div className="admin-grid">
        <div className="admin-card">
          <h3>Canchas</h3>
          <QueryState
            isLoading={courtsQuery.isLoading}
            error={courtsQuery.error}
            isEmpty={courts.length === 0}
            loading="Cargando canchas..."
            empty="Esta sede aún no tiene canchas."
          />
          <div className="mini-list">
            {courts.map((court) => editingCourtId === court.id ? (
              <div key={court.id} className="mini-item editing">
                <CourtForm venueId={venueId} court={court} onSaved={courtSaved} onCancel={() => setEditingCourtId(null)} />
              </div>
            ) : (
              <div key={court.id} className="mini-item">
                <div>
                  <strong>{court.name}</strong>
                  <p>{courtTypeLabel(court.courtType)}</p>
                  <ActiveTag active={court.active} />
                </div>
                <div className="user-actions">
                  <button type="button" className="secondary small-button" onClick={() => setEditingCourtId(court.id)}>Editar</button>
                  <Link to={`/admin/canchas/${court.id}/horario`} className="primary link-button small-button">Horario y bloqueos</Link>
                </div>
              </div>
            ))}
          </div>

          <h3>Agregar cancha</h3>
          <CourtForm venueId={venueId} onSaved={courtSaved} />
        </div>

        <div className="admin-card">
          <h3>Datos de la sede</h3>
          <VenueForm
            key={venue.id}
            venue={venue}
            onSaved={(saved) => {
              queryClient.setQueryData(['venue', venueId], saved)
              queryClient.invalidateQueries({ queryKey: ['my-venues'] })
            }}
          />
        </div>
      </div>
    </section>
  )
}
