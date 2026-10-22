// Referencia para el equipo: todos los endpoints de la API agrupados por área.
// Debe coincidir con los controladores del backend.

export function BackendRoutesPage() {
  const routeGroups = [
    { title: 'Autenticación', access: 'Público', items: ['POST /api/v1/auth/register', 'POST /api/v1/auth/login', 'POST /api/v1/auth/refresh', 'POST /api/v1/auth/logout', 'POST /api/v1/auth/password/forgot', 'POST /api/v1/auth/password/reset'] },
    { title: 'Usuarios', access: 'Autenticado', items: ['GET /api/v1/users/me', 'PATCH /api/v1/users/me', 'PUT /api/v1/users/me/password', 'DELETE /api/v1/users/me/sessions'] },
    { title: 'Sedes', access: 'Público + admin', items: ['GET /api/v1/venues', 'GET /api/v1/venues/{id}', 'POST /api/v1/venues', 'PUT /api/v1/venues/{venueId}', 'GET /api/v1/venues/mine'] },
    { title: 'Canchas', access: 'Público + admin', items: ['GET /api/v1/venues/{venueId}/courts', 'GET /api/v1/courts/{courtId}', 'POST /api/v1/venues/{venueId}/courts', 'PUT /api/v1/courts/{courtId}'] },
    { title: 'Reservas', access: 'Autenticado + admin', items: ['GET /api/v1/courts/{courtId}/availability', 'POST /api/v1/reservations', 'GET /api/v1/reservations/mine', 'GET /api/v1/reservations/{reservationId}', 'POST /api/v1/reservations/{reservationId}/cancel', 'POST /api/v1/reservations/{reservationId}/complete', 'POST /api/v1/reservations/{reservationId}/no-show', 'GET /api/v1/venues/{venueId}/reservations'] },
    { title: 'Horarios y bloqueos', access: 'Admin', items: ['GET /api/v1/courts/{courtId}/schedules', 'PUT /api/v1/courts/{courtId}/schedules', 'GET /api/v1/courts/{courtId}/blocks', 'POST /api/v1/courts/{courtId}/blocks', 'DELETE /api/v1/courts/{courtId}/blocks/{blockId}'] },
    { title: 'Reportes', access: 'Admin / Super admin', items: ['GET /api/v1/venues/{venueId}/reports/summary', 'GET /api/v1/venues/{venueId}/reports/daily', 'GET /api/v1/venues/{venueId}/reports/demand', 'GET /api/v1/admin/reports/summary'] },
    { title: 'Administración de usuarios', access: 'Super admin', items: ['GET /api/v1/admin/users', 'GET /api/v1/admin/users/{userId}', 'GET /api/v1/admin/users/{userId}/venues', 'PUT /api/v1/admin/users/{userId}/roles', 'PUT /api/v1/admin/users/{userId}/status'] },
    { title: 'Festivos', access: 'Público', items: ['GET /api/v1/holidays?year={año}'] },
    { title: 'Salud del sistema', access: 'Público', items: ['GET /api/v1/health'] },
  ]

  return (
    <section className="content-panel routes-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Cobertura</p>
          <h1>Mapa de rutas del backend</h1>
        </div>
      </div>

      <p className="helper-copy">
        Todos los endpoints de la API agrupados por área. La documentación interactiva está en Swagger: http://localhost:8080/swagger-ui.html
      </p>

      <div className="routes-grid">
        {routeGroups.map((group) => (
          <article key={group.title} className="route-card">
            <div className="route-card-head">
              <h3>{group.title}</h3>
              <span className="route-badge">{group.access}</span>
            </div>
            <ul>
              {group.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </article>
        ))}
      </div>
    </section>
  )
}
