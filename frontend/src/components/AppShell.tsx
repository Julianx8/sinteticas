import { useEffect, useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import { hasAnyRole, useAuth } from '../auth/AuthContext'
import { CalendarIcon, FieldIcon, HomeIcon, LoginIcon, ShieldIcon, UserIcon } from './icons'

export function AppShell({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user, logout } = useAuth()
  const navigate = useNavigate()
  const isOwner = hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')
  const isSuperAdmin = hasAnyRole(user, 'SUPER_ADMIN')

  return (
    <div className="app-shell">
      <a href="#contenido" className="skip-link">Saltar al contenido</a>
      <header className="topbar">
        <div className="brand-wrap">
          <Link to="/" className="brand">
            Sintéticas
          </Link>
        </div>

        <nav className="main-nav" aria-label="Navegación principal">
          <NavLink to="/" end>Inicio</NavLink>
          {isAuthenticated && <NavLink to="/mis-reservas">Mis reservas</NavLink>}
          {isOwner && (
            <NavLink to="/admin" end className="nav-admin-link">
              Mis sedes
            </NavLink>
          )}
          {isSuperAdmin && <NavLink to="/admin/usuarios">Usuarios</NavLink>}
          {isSuperAdmin && <NavLink to="/admin/reportes">Plataforma</NavLink>}
          {isSuperAdmin && <NavLink to="/backend-rutas">Rutas API</NavLink>}
        </nav>

        <div className="auth-actions">
          {isAuthenticated ? (
            <>
              <Link to="/mi-perfil" className="user-pill">{user?.fullName ?? 'Mi perfil'}</Link>
              <button type="button" className="secondary" onClick={() => { logout(); navigate('/') }}>
                Cerrar sesión
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="secondary link-button">
                Iniciar sesión
              </Link>
              <Link to="/register" className="primary link-button">
                Crear cuenta
              </Link>
            </>
          )}
        </div>
      </header>

      <OfflineBanner />
      <main className="page-content" id="contenido">{children}</main>

      {/* En celular la navegación va abajo, al alcance del pulgar (ver styles.css) */}
      <nav className="bottom-nav" aria-label="Navegación inferior">
        <NavLink to="/" end><HomeIcon /><span>Inicio</span></NavLink>
        {isAuthenticated && <NavLink to="/mis-reservas"><CalendarIcon /><span>Reservas</span></NavLink>}
        {isOwner && <NavLink to="/admin"><FieldIcon /><span>Mis sedes</span></NavLink>}
        {isSuperAdmin && <NavLink to="/admin/usuarios"><ShieldIcon /><span>Admin</span></NavLink>}
        {isAuthenticated
          ? <NavLink to="/mi-perfil"><UserIcon /><span>Perfil</span></NavLink>
          : <NavLink to="/login"><LoginIcon /><span>Entrar</span></NavLink>}
      </nav>
    </div>
  )
}

/** Franja visible mientras el dispositivo no tiene internet. */
function OfflineBanner() {
  const [online, setOnline] = useState(() => navigator.onLine)
  useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [])
  if (online) return null
  return <div className="offline-banner" role="status">Sin conexión. Puedes ver tus reservas guardadas; para reservar necesitas internet.</div>
}
