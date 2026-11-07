import { Link } from 'react-router-dom'
import { usePageTitle } from '../components/ui'

export function NotFoundPage() {
  usePageTitle('Página no encontrada')
  return (
    <section className="content-panel empty-page">
      <p className="eyebrow">Error 404</p>
      <h1>Esta página no existe</h1>
      <p className="muted">Puede que el enlace esté mal escrito o que la página se haya movido.</p>
      <div className="venue-actions">
        <Link to="/" className="primary link-button">Buscar canchas</Link>
        <Link to="/mis-reservas" className="secondary link-button">Mis reservas</Link>
      </div>
    </section>
  )
}
