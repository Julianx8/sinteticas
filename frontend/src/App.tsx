import { QueryClientProvider } from '@tanstack/react-query'
import { Suspense, lazy } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { queryClient } from './api/client'
import { AuthProvider, RequireAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { ErrorBoundary } from './components/ErrorBoundary'
import { ConfirmProvider, Skeleton } from './components/ui'
import { AuthPage } from './pages/auth/AuthPage'
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ProfilePage } from './pages/ProfilePage'
import { ReservationDetailPage } from './pages/ReservationDetailPage'
import { ReservationsPage } from './pages/ReservationsPage'
import { VenueDetailPage } from './pages/VenueDetailPage'

// Las páginas del dueño y del super admin se descargan solo cuando alguien entra a ellas:
// un cliente (la mayoría) no carga ese código.
const named = <T, K extends keyof T>(loader: () => Promise<T>, name: K) =>
  lazy(() => loader().then((module) => ({ default: module[name] as React.ComponentType })))

const OwnerDashboardPage = named(() => import('./pages/owner/OwnerDashboardPage'), 'OwnerDashboardPage')
const VenueManagePage = named(() => import('./pages/owner/VenueManagePage'), 'VenueManagePage')
const VenueAgendaPage = named(() => import('./pages/owner/VenueAgendaPage'), 'VenueAgendaPage')
const VenueReportsPage = named(() => import('./pages/owner/VenueReportsPage'), 'VenueReportsPage')
const CourtSchedulePage = named(() => import('./pages/owner/CourtSchedulePage'), 'CourtSchedulePage')
const AdminUsersPage = named(() => import('./pages/admin/AdminUsersPage'), 'AdminUsersPage')
const PlatformReportsPage = named(() => import('./pages/admin/PlatformReportsPage'), 'PlatformReportsPage')
const BackendRoutesPage = named(() => import('./pages/BackendRoutesPage'), 'BackendRoutesPage')

/*
 * Estructura:
 *   api/         cliente HTTP (sesión, refresh token, errores, copias sin conexión) y tipos de los DTO
 *   auth/        contexto de sesión y protección de rutas
 *   components/  layout y piezas reutilizables (estados, diálogos, íconos, gráficas)
 *   lib/         formato, errores amigables y acciones para compartir
 *   pages/       una página por ruta (owner/ = dueño de sede, admin/ = super admin)
 */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <ConfirmProvider>
            <AppShell>
              <AppRoutes />
            </AppShell>
          </ConfirmProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

function AppRoutes() {
  const location = useLocation()
  const owner = (page: React.ReactNode) => <RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}>{page}</RequireAuth>
  const superAdmin = (page: React.ReactNode) => <RequireAuth roles={['SUPER_ADMIN']}>{page}</RequireAuth>

  return (
    // key: al cambiar de página se limpia un error anterior
    <ErrorBoundary key={location.pathname}>
      <Suspense fallback={<div className="content-panel"><Skeleton label="Cargando página..." rows={4} /></div>}>
        <Routes>
          {/* Públicas */}
          <Route path="/" element={<HomePage />} />
          <Route path="/venues/:venueId" element={<VenueDetailPage />} />
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route path="/olvide-contrasena" element={<ForgotPasswordPage />} />
          {/* El backend arma este enlace en el correo (PasswordResetService) */}
          <Route path="/restablecer-contrasena" element={<ResetPasswordPage />} />

          {/* Cualquier usuario con sesión */}
          <Route path="/mis-reservas" element={<RequireAuth><ReservationsPage /></RequireAuth>} />
          <Route path="/reservas/:reservationId" element={<RequireAuth><ReservationDetailPage /></RequireAuth>} />
          <Route path="/mi-perfil" element={<RequireAuth><ProfilePage /></RequireAuth>} />

          {/* Dueño de sede */}
          <Route path="/admin" element={owner(<OwnerDashboardPage />)} />
          <Route path="/admin/sedes/:venueId" element={owner(<VenueManagePage />)} />
          <Route path="/admin/sedes/:venueId/agenda" element={owner(<VenueAgendaPage />)} />
          <Route path="/admin/sedes/:venueId/reportes" element={owner(<VenueReportsPage />)} />
          <Route path="/admin/canchas/:courtId/horario" element={owner(<CourtSchedulePage />)} />

          {/* Super admin */}
          <Route path="/admin/usuarios" element={superAdmin(<AdminUsersPage />)} />
          <Route path="/admin/reportes" element={superAdmin(<PlatformReportsPage />)} />
          <Route path="/backend-rutas" element={superAdmin(<BackendRoutesPage />)} />

          {/* Rutas anteriores, por si alguien las guardó */}
          <Route path="/mis-sedes" element={<Navigate to="/admin" replace />} />
          <Route path="/sedes/:venueId/reportes" element={<RedirectWithParam to={(id) => `/admin/sedes/${id}/reportes`} param="venueId" />} />
          <Route path="/canchas/:courtId/horarios" element={<RedirectWithParam to={(id) => `/admin/canchas/${id}/horario`} param="courtId" />} />

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </Suspense>
    </ErrorBoundary>
  )
}

function RedirectWithParam({ to, param }: { to: (value: string) => string; param: string }) {
  const params = useParams()
  return <Navigate to={to(params[param] ?? '')} replace />
}
