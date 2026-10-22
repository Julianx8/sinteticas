/** Panel ilustrado + formulario, compartido por las páginas de cuenta. */
export function AuthLayout({ eyebrow, title, children }: { eyebrow: string; title: string; children: React.ReactNode }) {
  return (
    <section className="auth-shell">
      <div className="auth-visual-panel">
        <div className="auth-visual-inner">
          <p className="eyebrow warm">Bienvenido</p>
          <h2>Organiza tus partidos sin complicaciones.</h2>
          <ul>
            <li>Reserva canchas en segundos</li>
            <li>Consulta la disponibilidad real</li>
            <li>Gestiona tus sedes y horarios</li>
          </ul>
          <div className="visual-badges">
            <span>⚽ Fútbol</span>
            <span>🏟️ Sedes</span>
            <span>📅 Agenda</span>
          </div>
        </div>
      </div>

      <div className="form-panel auth-panel">
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {children}
      </div>
    </section>
  )
}
