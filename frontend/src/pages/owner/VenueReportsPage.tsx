import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch } from '../../api/client'
import type { Metrics, VenueDailyReport, VenueDemandReport, VenueSummaryReport } from '../../api/types'
import { DailyBarChart, DemandHeatmap } from '../../components/Charts'
import { DateRange, PageHeading, QueryState } from '../../components/ui'
import { DAY_NAMES, addDays, formatHour, formatPercent, formatPrice, todayIso } from '../../lib/format'

export function VenueReportsPage() {
  const { venueId = '' } = useParams()
  const today = todayIso()
  const [range, setRange] = useState({ from: addDays(today, -29), to: today })
  const params = `from=${range.from}&to=${range.to}`

  const summaryQuery = useQuery({
    queryKey: ['venue-reports', 'summary', venueId, range.from, range.to],
    queryFn: () => apiFetch<VenueSummaryReport>(`/api/v1/venues/${venueId}/reports/summary?${params}`),
  })
  const dailyQuery = useQuery({
    queryKey: ['venue-reports', 'daily', venueId, range.from, range.to],
    queryFn: () => apiFetch<VenueDailyReport>(`/api/v1/venues/${venueId}/reports/daily?${params}`),
  })
  const demandQuery = useQuery({
    queryKey: ['venue-reports', 'demand', venueId, range.from, range.to],
    queryFn: () => apiFetch<VenueDemandReport>(`/api/v1/venues/${venueId}/reports/demand?${params}`),
  })

  const summary = summaryQuery.data

  return (
    <section className="content-panel routes-page">
      <PageHeading eyebrow="Reportes" title={summary?.venueName ?? 'Sede'}>
        <Link to={`/admin/sedes/${venueId}`} className="secondary link-button">Gestionar sede</Link>
      </PageHeading>

      <div className="filter-row">
        <DateRange from={range.from} to={range.to} onChange={(from, to) => setRange({ from, to })} />
        <div className="quick-ranges">
          {[7, 30, 90].map((days) => (
            <button key={days} type="button" className="ghost-button" onClick={() => setRange({ from: addDays(today, -(days - 1)), to: today })}>
              Últimos {days} días
            </button>
          ))}
        </div>
      </div>

      <QueryState isLoading={summaryQuery.isLoading} error={summaryQuery.error} loading="Calculando reporte..." />
      {summary && <MetricTiles metrics={summary.totals} />}

      <div className="admin-card">
        <h3>Reservas por día</h3>
        <QueryState isLoading={dailyQuery.isLoading} error={dailyQuery.error} loading="Cargando..." />
        {dailyQuery.data && <DailyBarChart days={dailyQuery.data.days} />}
      </div>

      <div className="admin-grid">
        <div className="admin-card">
          <h3>¿Cuándo se reserva más?</h3>
          <QueryState isLoading={demandQuery.isLoading} error={demandQuery.error} loading="Cargando..." />
          {demandQuery.data && <DemandHeatmap cells={demandQuery.data.cells} />}
        </div>
        <div className="admin-card">
          <h3>Horas pico</h3>
          {demandQuery.data && demandQuery.data.peakHours.length === 0 && <p className="muted">Sin datos en este rango.</p>}
          <ol className="peak-list">
            {demandQuery.data?.peakHours.map((cell) => (
              <li key={`${cell.dayOfWeek}-${cell.hour}`}>
                <strong>{DAY_NAMES[cell.dayOfWeek]} {formatHour(cell.hour)}</strong> · {cell.bookedHours} h reservadas
              </li>
            ))}
          </ol>
          <p className="muted">Útil para ajustar precios por franja en el horario de cada cancha.</p>
        </div>
      </div>

      {summary && summary.courts.length > 0 && (
        <div className="admin-card">
          <h3>Por cancha</h3>
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr><th>Cancha</th><th>Reservas</th><th>Ocupación</th><th>Cancelación</th><th>No asistió</th><th>Ingresos realizados</th><th>Por cobrar</th></tr>
              </thead>
              <tbody>
                {summary.courts.map((court) => (
                  <tr key={court.courtId}>
                    <td>{court.courtName}{!court.active && <span className="muted"> (inactiva)</span>}</td>
                    <td>{court.metrics.reservations}</td>
                    <td>{formatPercent(court.metrics.occupancyRate)}</td>
                    <td>{formatPercent(court.metrics.cancellationRate)}</td>
                    <td>{formatPercent(court.metrics.noShowRate)}</td>
                    <td>{formatPrice(court.metrics.realizedRevenue)}</td>
                    <td>{formatPrice(court.metrics.projectedRevenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  )
}

/** Indicadores principales como cifras sueltas (no necesitan gráfica). */
export function MetricTiles({ metrics }: { metrics: Metrics }) {
  return (
    <div className="admin-summary-grid metric-tiles">
      <Tile label="Reservas" value={String(metrics.reservations)} hint={`${metrics.completed} completadas · ${metrics.cancelled} canceladas`} />
      {metrics.occupancyRate !== undefined && (
        <Tile label="Ocupación" value={formatPercent(metrics.occupancyRate)} hint={`${metrics.bookedHours} de ${metrics.availableHours ?? 0} horas`} />
      )}
      <Tile label="Ingresos realizados" value={formatPrice(metrics.realizedRevenue)} hint="reservas completadas" />
      <Tile label="Por cobrar" value={formatPrice(metrics.projectedRevenue)} hint="reservas confirmadas" />
      <Tile label="Cancelación" value={formatPercent(metrics.cancellationRate)} hint={`No asistió: ${formatPercent(metrics.noShowRate)}`} />
    </div>
  )
}

function Tile({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="summary-tile">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{hint}</small>
    </div>
  )
}
