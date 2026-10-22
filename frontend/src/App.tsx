import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { queryClient } from './api/client'
import { AuthProvider, RequireAuth } from './auth/AuthContext'
import { AppShell } from './components/AppShell'
import { AdminUsersPage } from './pages/admin/AdminUsersPage'
import { PlatformReportsPage } from './pages/admin/PlatformReportsPage'
import { AuthPage } from './pages/auth/AuthPage'
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage'
import { BackendRoutesPage } from './pages/BackendRoutesPage'
import { HomePage } from './pages/HomePage'
import { CourtSchedulePage } from './pages/owner/CourtSchedulePage'
import { OwnerDashboardPage } from './pages/owner/OwnerDashboardPage'
import { VenueAgendaPage } from './pages/owner/VenueAgendaPage'
import { VenueManagePage } from './pages/owner/VenueManagePage'
import { VenueReportsPage } from './pages/owner/VenueReportsPage'
import { ProfilePage } from './pages/ProfilePage'
import { ReservationDetailPage } from './pages/ReservationDetailPage'
import { ReservationsPage } from './pages/ReservationsPage'
import { VenueDetailPage } from './pages/VenueDetailPage'

/*
 * Estructura:
 *   api/         cliente HTTP (sesión, refresh token, errores) y tipos de los DTO
 *   auth/        contexto de sesión y protección de rutas
 *   components/  layout y piezas reutilizables (estados, gráficas)
 *   lib/         formato de fechas, precios y textos
 *   pages/       una página por ruta (owner/ = dueño de sede, admin/ = super admin)
 */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <AppShell>
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
              <Route path="/admin" element={<RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}><OwnerDashboardPage /></RequireAuth>} />
              <Route path="/admin/sedes/:venueId" element={<RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}><VenueManagePage /></RequireAuth>} />
              <Route path="/admin/sedes/:venueId/agenda" element={<RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}><VenueAgendaPage /></RequireAuth>} />
              <Route path="/admin/sedes/:venueId/reportes" element={<RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}><VenueReportsPage /></RequireAuth>} />
              <Route path="/admin/canchas/:courtId/horario" element={<RequireAuth roles={['ADMIN', 'SUPER_ADMIN']}><CourtSchedulePage /></RequireAuth>} />

              {/* Super admin */}
              <Route path="/admin/usuarios" element={<RequireAuth roles={['SUPER_ADMIN']}><AdminUsersPage /></RequireAuth>} />
              <Route path="/admin/reportes" element={<RequireAuth roles={['SUPER_ADMIN']}><PlatformReportsPage /></RequireAuth>} />
              <Route path="/backend-rutas" element={<RequireAuth roles={['SUPER_ADMIN']}><BackendRoutesPage /></RequireAuth>} />

              {/* Rutas anteriores, por si alguien las guardó */}
              <Route path="/mis-sedes" element={<Navigate to="/admin" replace />} />
              <Route path="/sedes/:venueId/reportes" element={<RedirectWithParam to={(id) => `/admin/sedes/${id}/reportes`} param="venueId" />} />
              <Route path="/canchas/:courtId/horarios" element={<RedirectWithParam to={(id) => `/admin/canchas/${id}/horario`} param="courtId" />} />

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AppShell>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

function RedirectWithParam({ to, param }: { to: (value: string) => string; param: string }) {
  const params = useParams()
  return <Navigate to={to(params[param] ?? '')} replace />
}
