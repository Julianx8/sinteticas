import { useState } from 'react'
import type { DemandCell, VenueDailyReport } from '../api/types'
import { DAY_NAMES, DAY_SHORT, formatDate, formatHour, formatPrice } from '../lib/format'

/** Reservas por día (una sola serie: no lleva leyenda). Pasar el mouse muestra el detalle del día. */
export function DailyBarChart({ days }: { days: VenueDailyReport['days'] }) {
  const [hovered, setHovered] = useState<number | null>(null)
  const values = days.map((day) => day.metrics.reservations - day.metrics.cancelled)
  const max = Math.max(1, ...values)
  const active = hovered !== null ? days[hovered] : null

  return (
    <figure className="chart">
      <figcaption className="chart-caption">
        {active
          ? <><strong>{formatDate(active.date)}</strong> · {values[hovered!]} reservas · {formatPrice(Number(active.metrics.realizedRevenue) + Number(active.metrics.projectedRevenue))}</>
          : <span className="muted">Reservas por día (sin canceladas). Pasa el mouse por una barra para ver el detalle.</span>}
      </figcaption>
      <div className="bar-chart" role="img" aria-label={`Reservas por día, máximo ${max}`} onMouseLeave={() => setHovered(null)}>
        <div className="bar-axis" aria-hidden="true">
          <span>{max}</span>
          <span>0</span>
        </div>
        <div className="bar-plot">
          {days.map((day, index) => (
            <div
              key={day.date}
              className={`bar-slot ${hovered === index ? 'hovered' : ''}`}
              onMouseEnter={() => setHovered(index)}
              onFocus={() => setHovered(index)}
              tabIndex={0}
              aria-label={`${formatDate(day.date)}: ${values[index]} reservas`}
            >
              <div className="bar" style={{ height: `${(values[index] / max) * 100}%` }} />
            </div>
          ))}
        </div>
      </div>
      <div className="bar-labels" aria-hidden="true">
        <span>{formatDate(days[0]?.date ?? '')}</span>
        <span>{formatDate(days[days.length - 1]?.date ?? '')}</span>
      </div>
      <details className="chart-table">
        <summary>Ver como tabla</summary>
        <table className="data-table">
          <thead><tr><th>Día</th><th>Reservas</th><th>Canceladas</th><th>Horas</th><th>Ingresos</th></tr></thead>
          <tbody>
            {days.map((day) => (
              <tr key={day.date}>
                <td>{formatDate(day.date)}</td>
                <td>{day.metrics.reservations - day.metrics.cancelled}</td>
                <td>{day.metrics.cancelled}</td>
                <td>{day.metrics.bookedHours}</td>
                <td>{formatPrice(Number(day.metrics.realizedRevenue) + Number(day.metrics.projectedRevenue))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  )
}

/** Mapa de calor día × hora: más oscuro = más horas reservadas (un solo tono, de claro a oscuro). */
export function DemandHeatmap({ cells }: { cells: DemandCell[] }) {
  const [hovered, setHovered] = useState<DemandCell | null>(null)
  if (cells.length === 0) return <div className="status-box">No hay reservas en este rango.</div>

  const byKey = new Map(cells.map((cell) => [`${cell.dayOfWeek}-${cell.hour}`, cell.bookedHours]))
  const max = Math.max(...cells.map((cell) => cell.bookedHours))
  const minHour = Math.min(...cells.map((cell) => cell.hour))
  const maxHour = Math.max(...cells.map((cell) => cell.hour))
  const hours = Array.from({ length: maxHour - minHour + 1 }, (_, index) => minHour + index)

  return (
    <figure className="chart">
      <figcaption className="chart-caption">
        {hovered
          ? <><strong>{DAY_NAMES[hovered.dayOfWeek]} {formatHour(hovered.hour)}</strong> · {hovered.bookedHours} hora{hovered.bookedHours === 1 ? '' : 's'} reservada{hovered.bookedHours === 1 ? '' : 's'}</>
          : <span className="muted">Horas reservadas por día y hora. Más oscuro = más demanda.</span>}
      </figcaption>
      <div className="heatmap" style={{ gridTemplateColumns: `3rem repeat(${hours.length}, minmax(1.6rem, 2.2rem))` }} onMouseLeave={() => setHovered(null)}>
        <span />
        {hours.map((hour) => <span key={hour} className="heatmap-label">{hour}</span>)}
        {[1, 2, 3, 4, 5, 6, 7].map((day) => (
          <Row key={day} day={day} hours={hours} byKey={byKey} max={max} onHover={setHovered} />
        ))}
      </div>
      <div className="heatmap-legend" aria-hidden="true">
        <span>0</span>
        <div className="heatmap-ramp" />
        <span>{max} h</span>
      </div>
    </figure>
  )
}

function Row({ day, hours, byKey, max, onHover }: {
  day: number
  hours: number[]
  byKey: Map<string, number>
  max: number
  onHover: (cell: DemandCell) => void
}) {
  return (
    <>
      <span className="heatmap-label">{DAY_SHORT[day]}</span>
      {hours.map((hour) => {
        const value = byKey.get(`${day}-${hour}`) ?? 0
        // Escala de 15 % a 100 % del tono; las celdas en 0 quedan vacías
        const strength = value === 0 ? 0 : 15 + (value / max) * 85
        return (
          <div
            key={hour}
            className="heatmap-cell"
            style={{ ['--strength' as string]: `${strength}%` }}
            tabIndex={value > 0 ? 0 : -1}
            aria-label={`${DAY_NAMES[day]} ${formatHour(hour)}: ${value} horas`}
            onMouseEnter={() => onHover({ dayOfWeek: day, hour, bookedHours: value })}
            onFocus={() => onHover({ dayOfWeek: day, hour, bookedHours: value })}
          />
        )
      })}
    </>
  )
}
