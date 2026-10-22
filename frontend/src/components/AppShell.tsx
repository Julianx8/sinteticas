import { Link, NavLink, useNavigate } from 'react-router-dom'
import { hasAnyRole, useAuth } from '../auth/AuthContext'

export function AppShell({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, user, logout } = useAuth()
  const navigate = useNavigate()
  const isOwner = hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')
  const isSuperAdmin = hasAnyRole(user, 'SUPER_ADMIN')

  return (
    <div className="app-shell">
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

      <main className="page-content">{children}</main>
    </div>
  )
}
