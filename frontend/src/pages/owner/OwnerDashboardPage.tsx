import { useQueries, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiFetch, queryClient } from '../../api/client'
import type { PageResponse, Venue, VenueSummaryReport } from '../../api/types'
import { ActiveTag, QueryState } from '../../components/ui'
import { formatPrice, todayIso } from '../../lib/format'
import { VenueForm } from './forms'

export function OwnerDashboardPage() {
  const navigate = useNavigate()
  const [creating, setCreating] = useState(false)

  const venuesQuery = useQuery({
    queryKey: ['my-venues'],
    queryFn: () => apiFetch<PageResponse<Venue>>('/api/v1/venues/mine?size=100&sort=name'),
  })
  const venues = venuesQuery.data?.content ?? []

  // Cifras reales del día: el resumen de cada sede, sumado
  const today = todayIso()
  const todayReports = useQueries({
    queries: venues.map((venue) => ({
      queryKey: ['venue-reports', 'summary', venue.id, today, today],
      queryFn: () => apiFetch<VenueSummaryReport>(`/api/v1/venues/${venue.id}/reports/summary?from=${today}&to=${today}`),
    })),
  })
  const todayTotals = todayReports.reduce(
    (sum, report) => {
      const totals = report.data?.totals
      if (!totals) return sum
      return {
        reservations: sum.reservations + totals.reservations - totals.cancelled,
        revenue: sum.revenue + Number(totals.realizedRevenue) + Number(totals.projectedRevenue),
      }
    },
    { reservations: 0, revenue: 0 },
  )
  // Mientras llegan los reportes no se muestra un 0 que parezca real
  const loadingToday = venuesQuery.isLoading || todayReports.some((report) => report.isLoading)

  return (
    <section className="admin-dashboard-shell">
      <div className="admin-hero">
        <div>
          <p className="eyebrow warm">Administración</p>
          <h1>Mis sedes</h1>
        </div>
        <button type="button" className="primary cta-button small-button" onClick={() => setCreating(!creating)}>
          {creating ? 'Cerrar' : 'Nueva sede'}
        </button>
      </div>

      <div className="admin-summary-grid">
        <div className="summary-tile soft-accent">
          <span>Sedes activas</span>
          <strong>{venues.filter((venue) => venue.active).length}</strong>
          <small>de {venues.length} registradas</small>
        </div>
        <div className="summary-tile">
          <span>Reservas hoy</span>
          <strong>{loadingToday ? '—' : todayTotals.reservations}</strong>
          <small>sin contar canceladas</small>
        </div>
        <div className="summary-tile">
          <span>Ingreso del día</span>
          <strong>{loadingToday ? '—' : formatPrice(todayTotals.revenue)}</strong>
          <small>completadas + confirmadas</small>
        </div>
      </div>

      {creating && (
        <div className="admin-card admin-form-panel">
          <h2>Nueva sede</h2>
          <VenueForm
            onSaved={(venue) => {
              queryClient.invalidateQueries({ queryKey: ['my-venues'] })
              navigate(`/admin/sedes/${venue.id}`)
            }}
          />
        </div>
      )}

      <div className="admin-card admin-list-card">
        <QueryState
          isLoading={venuesQuery.isLoading}
          error={venuesQuery.error}
          isEmpty={venues.length === 0}
          loading="Cargando sedes..."
          empty="Todavía no tienes sedes. Crea la primera con «Nueva sede»."
        />
        <div className="mini-list">
          {venues.map((venue) => (
            <div key={venue.id} className="mini-item">
              <div>
                <strong>{venue.name}</strong>
                <p>{venue.address}</p>
                <ActiveTag active={venue.active} />
              </div>
              <div className="user-actions">
                <Link to={`/admin/sedes/${venue.id}/agenda`} className="secondary link-button small-button">Agenda</Link>
                <Link to={`/admin/sedes/${venue.id}/reportes`} className="secondary link-button small-button">Reportes</Link>
                <Link to={`/admin/sedes/${venue.id}`} className="primary link-button small-button">Gestionar</Link>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
