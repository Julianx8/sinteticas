import { usePageTitle } from '../../components/ui'

/** Panel ilustrado + formulario, compartido por las páginas de cuenta. */
export function AuthLayout({ eyebrow, title, children }: { eyebrow: string; title: string; children: React.ReactNode }) {
  usePageTitle(title)
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
          <ol className="how-it-works">
            <li><strong>Elige sede y día</strong><span>Ves las horas libres de cada cancha, al instante.</span></li>
            <li><strong>Toca las horas</strong><span>De 1 a 3 horas seguidas, con el precio total a la vista.</span></li>
            <li><strong>Confirma y comparte</strong><span>Te llega el correo y lo mandas al grupo por WhatsApp.</span></li>
          </ol>
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
