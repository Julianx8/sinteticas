import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { apiFetch } from '../../api/client'
import type { PlatformSummaryReport } from '../../api/types'
import { DateRange, PageHeading, QueryState } from '../../components/ui'
import { addDays, formatPrice, todayIso } from '../../lib/format'
import { MetricTiles } from '../owner/VenueReportsPage'

export function PlatformReportsPage() {
  const today = todayIso()
  const [range, setRange] = useState({ from: addDays(today, -89), to: today })

  const reportQuery = useQuery({
    queryKey: ['platform-reports', range.from, range.to],
    queryFn: () => apiFetch<PlatformSummaryReport>(`/api/v1/admin/reports/summary?from=${range.from}&to=${range.to}`),
  })
  const report = reportQuery.data

  return (
    <section className="content-panel routes-page">
      <PageHeading eyebrow="Plataforma" title="Resumen global" />
      <DateRange from={range.from} to={range.to} onChange={(from, to) => setRange({ from, to })} />

      <QueryState isLoading={reportQuery.isLoading} error={reportQuery.error} loading="Calculando resumen..." />

      {report && (
        <>
          <div className="admin-summary-grid">
            <div className="summary-tile soft-accent"><span>Usuarios</span><strong>{report.totalUsers}</strong><small>{report.newUsers} nuevos en el rango</small></div>
            <div className="summary-tile"><span>Sedes activas</span><strong>{report.activeVenues}</strong><small>hoy</small></div>
            <div className="summary-tile"><span>Canchas activas</span><strong>{report.activeCourts}</strong><small>hoy</small></div>
          </div>

          <MetricTiles metrics={report.reservations} />

          <div className="admin-grid">
            <div className="admin-card">
              <h3>Por mes</h3>
              {report.months.length === 0 ? <p className="muted">Sin reservas en el rango.</p> : (
                <div className="table-wrap">
                  <table className="data-table">
                    <thead><tr><th>Mes</th><th>Reservas</th><th>Canceladas</th><th>Realizado</th><th>Por cobrar</th></tr></thead>
                    <tbody>
                      {report.months.map((month) => (
                        <tr key={month.month}>
                          <td>{month.month}</td>
                          <td>{month.reservations}</td>
                          <td>{month.cancelled}</td>
                          <td>{formatPrice(month.realizedRevenue)}</td>
                          <td>{formatPrice(month.projectedRevenue)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="admin-card">
              <h3>Sedes con más reservas</h3>
              {report.topVenues.length === 0 ? <p className="muted">Sin reservas en el rango.</p> : (
                <ol className="peak-list">
                  {report.topVenues.map((venue) => (
                    <li key={venue.venueId}>
                      <strong>{venue.venueName}</strong> · {venue.reservations} reservas · {formatPrice(venue.realizedRevenue)}
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </div>
        </>
      )}
    </section>
  )
}
