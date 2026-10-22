import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { apiFetch, queryClient } from '../../api/client'
import type { Block, Court, ScheduleSlot } from '../../api/types'
import { DateRange, ErrorBox, PageHeading, QueryState, SuccessBox } from '../../components/ui'
import { DAY_NAMES, addDays, courtTypeLabel, formatDateTime, formatHour, todayIso, withBogotaOffset } from '../../lib/format'

export function CourtSchedulePage() {
  const { courtId = '' } = useParams()
  const courtQuery = useQuery({
    queryKey: ['court', courtId],
    queryFn: () => apiFetch<Court>(`/api/v1/courts/${courtId}`),
  })
  const court = courtQuery.data
  if (!court) {
    return <QueryState isLoading={courtQuery.isLoading} error={courtQuery.error} loading="Cargando cancha..." />
  }

  return (
    <section className="content-panel routes-page">
      <PageHeading eyebrow={`${court.venueName} · ${courtTypeLabel(court.courtType)}`} title={court.name}>
        <Link to={`/admin/sedes/${court.venueId}`} className="secondary link-button">Volver a la sede</Link>
      </PageHeading>
      <WeeklyScheduleEditor courtId={courtId} />
      <BlocksSection courtId={courtId} />
    </section>
  )
}

const HOURS = Array.from({ length: 25 }, (_, hour) => hour)

function WeeklyScheduleEditor({ courtId }: { courtId: string }) {
  const scheduleQuery = useQuery({
    queryKey: ['court-schedule', courtId],
    queryFn: () => apiFetch<ScheduleSlot[]>(`/api/v1/courts/${courtId}/schedules`),
  })
  const [slots, setSlots] = useState<ScheduleSlot[]>([])
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    if (scheduleQuery.data) {
      setSlots(scheduleQuery.data)
      setDirty(false)
    }
  }, [scheduleQuery.data])

  const mutation = useMutation({
    mutationFn: () => apiFetch<ScheduleSlot[]>(`/api/v1/courts/${courtId}/schedules`, { method: 'PUT', body: { slots: sorted(slots) } }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['court-schedule', courtId], saved)
      queryClient.invalidateQueries({ queryKey: ['availability', courtId] })
    },
  })

  const edit = (next: ScheduleSlot[]) => {
    setSlots(next)
    setDirty(true)
    mutation.reset()
  }
  const update = (index: number, change: Partial<ScheduleSlot>) =>
    edit(slots.map((slot, i) => (i === index ? { ...slot, ...change } : slot)))
  const addSlot = () => {
    const last = slots[slots.length - 1]
    edit([...slots, last ? { ...last } : { dayOfWeek: 1, startHour: 8, endHour: 22, pricePerHour: 80000 }])
  }
  /** Copia las franjas del lunes al resto de la semana (reemplaza las de esos días). */
  const copyMondayToAll = () => {
    const monday = slots.filter((slot) => slot.dayOfWeek === 1)
    edit([...monday, ...[2, 3, 4, 5, 6, 7].flatMap((day) => monday.map((slot) => ({ ...slot, dayOfWeek: day })))])
  }

  return (
    <div className="admin-card">
      <h3>Horario semanal y precios</h3>
      <p className="muted">
        Cada franja indica las horas en que la cancha abre y el precio por hora. Puedes tener varias franjas el mismo día
        (p. ej. más cara en la noche), pero no pueden cruzarse. Los festivos usan el horario del domingo.
      </p>

      <QueryState isLoading={scheduleQuery.isLoading} error={scheduleQuery.error} loading="Cargando horario..." />

      {!scheduleQuery.isLoading && (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Día</th>
                <th>Abre</th>
                <th>Cierra</th>
                <th>Precio por hora</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {slots.length === 0 && (
                <tr><td colSpan={5} className="muted">Sin franjas: la cancha no se puede reservar.</td></tr>
              )}
              {slots.map((slot, index) => (
                <tr key={index}>
                  <td>
                    <select value={slot.dayOfWeek} onChange={(event) => update(index, { dayOfWeek: Number(event.target.value) })} aria-label="Día">
                      {Object.entries(DAY_NAMES).map(([day, name]) => <option key={day} value={day}>{name}</option>)}
                    </select>
                  </td>
                  <td>
                    <select value={slot.startHour} onChange={(event) => update(index, { startHour: Number(event.target.value) })} aria-label="Hora de apertura">
                      {HOURS.slice(0, 24).map((hour) => <option key={hour} value={hour}>{formatHour(hour)}</option>)}
                    </select>
                  </td>
                  <td>
                    <select value={slot.endHour} onChange={(event) => update(index, { endHour: Number(event.target.value) })} aria-label="Hora de cierre">
                      {HOURS.slice(1).map((hour) => <option key={hour} value={hour}>{hour === 24 ? '24:00 (medianoche)' : formatHour(hour)}</option>)}
                    </select>
                  </td>
                  <td>
                    <input
                      type="number"
                      min={0}
                      step={1000}
                      value={slot.pricePerHour}
                      onChange={(event) => update(index, { pricePerHour: Number(event.target.value) })}
                      aria-label="Precio por hora"
                    />
                  </td>
                  <td>
                    <button type="button" className="ghost-button" onClick={() => edit(slots.filter((_, i) => i !== index))}>Quitar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="venue-actions">
        <button type="button" className="secondary small-button" onClick={addSlot}>Agregar franja</button>
        <button type="button" className="secondary small-button" onClick={copyMondayToAll} disabled={!slots.some((slot) => slot.dayOfWeek === 1)}>
          Copiar el lunes a toda la semana
        </button>
        <button type="button" className="primary" onClick={() => mutation.mutate()} disabled={!dirty || mutation.isPending}>
          {mutation.isPending ? 'Guardando...' : 'Guardar horario'}
        </button>
      </div>
      <ErrorBox error={mutation.error} />
      <SuccessBox show={mutation.isSuccess}>Horario guardado. Las reservas ya hechas no cambian.</SuccessBox>
    </div>
  )
}

function sorted(slots: ScheduleSlot[]) {
  return [...slots].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startHour - b.startHour)
}

function BlocksSection({ courtId }: { courtId: string }) {
  const today = todayIso()
  const [range, setRange] = useState({ from: today, to: addDays(today, 30) })
  const [form, setForm] = useState({ startAt: '', endAt: '', reason: '' })

  const blocksQuery = useQuery({
    queryKey: ['court-blocks', courtId, range.from, range.to],
    queryFn: () => apiFetch<Block[]>(`/api/v1/courts/${courtId}/blocks?from=${range.from}&to=${range.to}`),
  })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['court-blocks', courtId] })
    queryClient.invalidateQueries({ queryKey: ['availability', courtId] })
  }

  const createMutation = useMutation({
    // El input datetime-local no trae zona; el backend exige una (hora de Colombia)
    mutationFn: () => apiFetch<Block>(`/api/v1/courts/${courtId}/blocks`, {
      method: 'POST',
      body: { startAt: withBogotaOffset(form.startAt), endAt: withBogotaOffset(form.endAt), reason: form.reason.trim() },
    }),
    onSuccess: () => {
      setForm({ startAt: '', endAt: '', reason: '' })
      refresh()
    },
  })
  const deleteMutation = useMutation({
    mutationFn: (blockId: string) => apiFetch(`/api/v1/courts/${courtId}/blocks/${blockId}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })

  const blocks = blocksQuery.data ?? []

  return (
    <div className="admin-grid">
      <div className="admin-card">
        <h3>Bloquear horario</h3>
        <p className="muted">Para mantenimiento, torneos o días de cierre. Si ya hay reservas en ese horario no se cancelan solas: cancélalas desde la agenda y avísale al cliente.</p>
        <form className="stack-form" onSubmit={(event) => { event.preventDefault(); createMutation.mutate() }}>
          <label>
            Desde
            <input type="datetime-local" step={3600} value={form.startAt} onChange={(event) => setForm((current) => ({ ...current, startAt: event.target.value }))} required />
          </label>
          <label>
            Hasta
            <input type="datetime-local" step={3600} value={form.endAt} min={form.startAt} onChange={(event) => setForm((current) => ({ ...current, endAt: event.target.value }))} required />
          </label>
          <label>
            Motivo
            <input value={form.reason} maxLength={255} placeholder="Mantenimiento de la grama" onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))} required />
          </label>
          <ErrorBox error={createMutation.error} />
          <SuccessBox show={createMutation.isSuccess}>Bloqueo creado.</SuccessBox>
          <button type="submit" className="primary" disabled={createMutation.isPending}>Crear bloqueo</button>
        </form>
      </div>

      <div className="admin-card">
        <h3>Bloqueos</h3>
        <DateRange from={range.from} to={range.to} onChange={(from, to) => setRange({ from, to })} />
        <QueryState
          isLoading={blocksQuery.isLoading}
          error={blocksQuery.error}
          isEmpty={blocks.length === 0}
          loading="Cargando bloqueos..."
          empty="No hay bloqueos en estas fechas."
        />
        <div className="mini-list">
          {blocks.map((block) => (
            <div key={block.id} className="mini-item">
              <div>
                <strong>{block.reason}</strong>
                <p>{formatDateTime(block.startAt)} → {formatDateTime(block.endAt)}</p>
              </div>
              <button type="button" className="secondary small-button" onClick={() => deleteMutation.mutate(block.id)} disabled={deleteMutation.isPending}>
                Quitar
              </button>
            </div>
          ))}
        </div>
        <ErrorBox error={deleteMutation.error} />
      </div>
    </div>
  )
}
