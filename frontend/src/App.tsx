import { createContext, useContext, useMemo, useState } from 'react'
import { BrowserRouter, Link, NavLink, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { QueryClient, QueryClientProvider, useMutation, useQuery } from '@tanstack/react-query'

type Role = 'CUSTOMER' | 'ADMIN' | 'SUPER_ADMIN'

type User = {
  id: string
  fullName: string
  email: string
  roles: Role[]
  enabled: boolean
  createdAt: string
}

type AuthResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type Venue = {
  id: string
  ownerId: string
  name: string
  address: string
  neighborhood: string
  phone: string
  description: string
  active: boolean
  createdAt: string
}

type Court = {
  id: string
  venueId: string
  venueName: string
  name: string
  courtType: string
  description: string
  active: boolean
}

type ReservationStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'

type AvailabilityStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED' | 'PAST'

type AvailabilitySlot = {
  hour: string
  start: string
  end: string
  price: number
  status: AvailabilityStatus
}

type AvailabilityResponse = {
  date: string
  courtId: string
  slots: AvailabilitySlot[]
}

type Reservation = {
  id: string
  status: ReservationStatus
  startAt: string
  endAt: string
  durationHours: number
  totalPrice: number
  notes: string
  court: {
    id: string
    name: string
    venueId: string
    venueName: string
    venueAddress: string
  }
  customer: {
    id: string
    fullName: string
    email: string
  }
  cancelledAt: string | null
  cancellationReason: string | null
  createdAt: string
}

type AuthFormValues = {
  email: string
  password: string
  fullName?: string
}

type ErrorPayload = {
  status?: number
  title?: string
  detail?: string
  code?: string
  errors?: Record<string, string>
}

const API_BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
const AUTH_STORAGE_KEY = 'sinteticas-auth'
const queryClient = new QueryClient()

const AuthContext = createContext<{
  token: string | null
  user: User | null
  isAuthenticated: boolean
  login: (values: AuthFormValues) => Promise<void>
  register: (values: AuthFormValues) => Promise<void>
  logout: () => void
}>({
  token: null,
  user: null,
  isAuthenticated: false,
  login: async () => undefined,
  register: async () => undefined,
  logout: () => undefined,
})

function hasAnyRole(user: User | null, ...roles: Role[]) {
  if (!user) return false
  return roles.some((role) => user.roles.includes(role))
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <AppShell />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(() => {
    if (typeof window === 'undefined') return null
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as { token: string | null }
      return parsed.token ?? null
    } catch {
      return null
    }
  })

  const [user, setUser] = useState<User | null>(() => {
    if (typeof window === 'undefined') return null
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null
    try {
      const parsed = JSON.parse(raw) as { user: User | null }
      return parsed.user ?? null
    } catch {
      return null
    }
  })

  const login = async (values: AuthFormValues) => {
    const response = await apiFetch<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: { email: values.email, password: values.password },
    })
    setToken(response.accessToken)
    setUser(response.user)
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token: response.accessToken, user: response.user }))
  }

  const register = async (values: AuthFormValues) => {
    const response = await apiFetch<AuthResponse>('/api/v1/auth/register', {
      method: 'POST',
      body: {
        fullName: values.fullName ?? '',
        email: values.email,
        password: values.password,
      },
    })
    setToken(response.accessToken)
    setUser(response.user)
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ token: response.accessToken, user: response.user }))
  }

  const logout = () => {
    setToken(null)
    setUser(null)
    window.localStorage.removeItem(AUTH_STORAGE_KEY)
  }

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(token && user),
      login,
      register,
      logout,
    }),
    [token, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function AppShell() {
  const { isAuthenticated, user, logout } = useContext(AuthContext)

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-wrap">
          <Link to="/" className="brand">
            Sintéticas
          </Link>
        </div>

        <nav className="main-nav" aria-label="Navegación principal">
          <NavLink to="/">Inicio</NavLink>
          <NavLink to="/mis-reservas">Reservas</NavLink>
          <NavLink to="/mi-perfil">Perfil</NavLink>
          <NavLink to="/mis-sedes">Sedes</NavLink>
          <NavLink to="/backend-rutas">Rutas backend</NavLink>
          {user && hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN') && (
            <NavLink to="/admin" className="nav-admin-link">
              Panel admin
            </NavLink>
          )}
          {user && hasAnyRole(user, 'SUPER_ADMIN') && (
            <NavLink to="/admin/usuarios">Usuarios</NavLink>
          )}
        </nav>

        <div className="auth-actions">
          {isAuthenticated ? (
            <>
              <span className="user-pill">{user?.fullName ?? 'Usuario'}</span>
              <button type="button" className="secondary" onClick={logout}>
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

      <main className="page-content">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<AuthPage mode="login" />} />
          <Route path="/register" element={<AuthPage mode="register" />} />
          <Route path="/venues/:venueId" element={<VenueDetailPage />} />
          <Route path="/backend-rutas" element={<BackendRoutesPage />} />
          <Route path="/reservas/:reservationId" element={<ReservationDetailPage />} />
          <Route path="/canchas/:courtId/horarios" element={<CourtSchedulePage />} />
          <Route path="/sedes/:venueId/reportes" element={<VenueReportsPage />} />
          <Route
            path="/mis-reservas"
            element={
              <ProtectedRoute>
                <ReservationsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/mi-perfil"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/mis-sedes"
            element={
              <ProtectedRoute>
                <MyVenuesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <ProtectedRoute>
                <AdminDashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/reportes"
            element={
              <ProtectedRoute>
                <SuperAdminRoute>
                  <PlatformReportsPage />
                </SuperAdminRoute>
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/usuarios"
            element={
              <ProtectedRoute>
                <SuperAdminRoute>
                  <AdminUsersPage />
                </SuperAdminRoute>
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

function HomePage() {
  const [query, setQuery] = useState('')
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['venues', query],
    queryFn: () => apiFetch<PageResponse<Venue>>(`/api/v1/venues?q=${encodeURIComponent(query)}`),
  })

  const venues = data?.content ?? []

  return (
    <div className="dashboard-shell">
      <section className="welcome-panel">
        <div className="welcome-copy">
          <p className="eyebrow warm">Tu próxima cancha</p>
          <h1>Reservá fácil, rápida y sin vueltas.</h1>
          <p>
            Mirá sedes, elegí horarios y organizá tus partidos con una experiencia más clara, más cómoda y más humana.
          </p>

          <div className="welcome-actions">
            <Link to="/register" className="primary cta-button">
              Reservar ahora
            </Link>
            <Link to="/mis-reservas" className="secondary cta-button">
              Ver mis reservas
            </Link>
          </div>
        </div>

        <div className="welcome-side">
          <div className="mini-stat accent">
            <span>Hoy</span>
            <strong>12 canchas</strong>
            <small>disponibles en 4 sedes</small>
          </div>
          <div className="mini-stat">
            <span>Próximo partido</span>
            <strong>18:30</strong>
            <small>La Candelaria · 7v7</small>
          </div>
        </div>
      </section>

      <section className="summary-grid">
        <div className="summary-tile">
          <span>Reservas este mes</span>
          <strong>2.4k</strong>
          <small>+18% vs mes anterior</small>
        </div>
        <div className="summary-tile">
          <span>Sedes activas</span>
          <strong>30+</strong>
          <small>En Girardot y alrededores</small>
        </div>
        <div className="summary-tile">
          <span>Valoración</span>
          <strong>4.9/5</strong>
          <small>Basada en jugadas recientes</small>
        </div>
      </section>

      <section className="content-panel dashboard-layout">
        <div className="left-column">
          <div className="panel-header">
            <div>
              <p className="eyebrow">Explorar</p>
              <h2>Sedes recomendadas</h2>
            </div>
            <button type="button" className="ghost-button">Ver todas</button>
          </div>

          <div className="search-row compact">
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Buscar por nombre o barrio"
              aria-label="Buscar sedes"
            />
          </div>

          {isLoading && <div className="status-box">Cargando sedes...</div>}
          {isError && <div className="status-box error">{formatError(error)}</div>}

          {!isLoading && !isError && venues.length === 0 && <div className="status-box">No encontramos sedes con ese filtro.</div>}

          <div className="card-grid dashboard-cards">
            {venues.map((venue) => (
              <article key={venue.id} className="venue-card">
                <div className="venue-topline">
                  <span className={`status-tag ${venue.active ? 'online' : 'offline'}`}>{venue.active ? 'Activa' : 'Inactiva'}</span>
                  <span className="rating-pill">★ 4.8</span>
                </div>

                <h3>{venue.name}</h3>
                <p className="location-line">{venue.address}</p>
                <p className="muted">{venue.neighborhood || 'Sin barrio'}</p>
                <p className="description">{venue.description || 'Sin descripción adicional.'}</p>

                <div className="venue-footer">
                  <span className="mini-price">Desde $120.000</span>
                  <Link to={`/venues/${venue.id}`} className="primary action-link">
                    Ver detalle
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </div>

        <aside className="right-column">
          <div className="side-panel">
            <div className="panel-header compact-header">
              <div>
                <p className="eyebrow">Agenda</p>
                <h3>Hoy</h3>
              </div>
            </div>

            <div className="schedule-list">
              <div className="schedule-item">
                <div className="schedule-time">18:00</div>
                <div>
                  <strong>7v7 premium</strong>
                  <small>La Candelaria</small>
                </div>
              </div>
              <div className="schedule-item">
                <div className="schedule-time">19:30</div>
                <div>
                  <strong>5v5 mixto</strong>
                  <small>Club Verde</small>
                </div>
              </div>
              <div className="schedule-item">
                <div className="schedule-time">21:00</div>
                <div>
                  <strong>Fútbol rápido</strong>
                  <small>Cancha del río</small>
                </div>
              </div>
            </div>
          </div>

          <div className="side-panel accent-panel">
            <p className="eyebrow">Recomendado</p>
            <h3>Cancha premium 7v7</h3>
            <p>Excelente para partidos con equipo completo y mayor comodidad.</p>
            <div className="score-row">
              <span>4.8 ★</span>
              <strong>$140.000</strong>
            </div>
          </div>
        </aside>
      </section>
    </div>
  )
}

function VenueDetailPage() {
  const { venueId } = useParams()
  const { token } = useContext(AuthContext)
  const [selectedDate, setSelectedDate] = useState(() => getTodayIso())

  const venueQuery = useQuery({
    queryKey: ['venue', venueId],
    queryFn: () => apiFetch<Venue>(`/api/v1/venues/${venueId ?? ''}`),
    enabled: Boolean(venueId),
  })

  const courtsQuery = useQuery({
    queryKey: ['courts', venueId],
    queryFn: () => apiFetch<Court[]>(`/api/v1/venues/${venueId ?? ''}/courts`, {}, token ?? undefined),
    enabled: Boolean(venueId),
  })

  if (venueQuery.isLoading || courtsQuery.isLoading) return <div className="status-box">Cargando detalle de la sede...</div>
  if (venueQuery.isError) return <div className="status-box error">{formatError(venueQuery.error)}</div>
  if (!venueQuery.data) return <div className="status-box">No se encontró la sede.</div>

  const venue = venueQuery.data

  return (
    <section className="detail-page">
      <div className="detail-hero content-panel">
        <div>
          <p className="eyebrow">Sede</p>
          <h1>{venue.name}</h1>
          <div className="detail-meta">
            <span className={`status-tag ${venue.active ? 'online' : 'offline'}`}>{venue.active ? 'Abierta' : 'Cerrada'}</span>
            <span className="rating-pill">★ 4.8</span>
          </div>
        </div>

        <div className="detail-address-card">
          <p>{venue.address}</p>
          <p>{venue.neighborhood || 'Barrio no informado'}</p>
          <p>{venue.phone || 'Sin teléfono'}</p>
        </div>
      </div>

      <div className="detail-summary-strip">
        <div className="summary-tile">
          <span>Cancha premium</span>
          <strong>{courtsQuery.data?.length ?? 0}</strong>
          <small>opciones activas</small>
        </div>
        <div className="summary-tile">
          <span>Horarios</span>
          <strong>18</strong>
          <small>disponibles hoy</small>
        </div>
        <div className="summary-tile">
          <span>Precio desde</span>
          <strong>$120k</strong>
          <small>por hora</small>
        </div>
      </div>

      <div className="content-panel detail-panel">
        <div className="date-picker-wrap">
          <label htmlFor="select-date">Selecciona fecha</label>
          <input
            id="select-date"
            type="date"
            value={selectedDate}
            onChange={(event) => setSelectedDate(event.target.value)}
          />
        </div>

        <div className="court-stack">
          {(courtsQuery.data ?? []).map((court) => (
            <CourtAvailabilityCard key={court.id} court={court} selectedDate={selectedDate} />
          ))}
        </div>
      </div>
    </section>
  )
}

function CourtAvailabilityCard({ court, selectedDate }: { court: Court; selectedDate: string }) {
  const { token, isAuthenticated } = useContext(AuthContext)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['availability', court.id, selectedDate],
    queryFn: () => apiFetch<AvailabilityResponse>(`/api/v1/courts/${court.id}/availability?date=${selectedDate}`, undefined, token ?? undefined),
  })

  const [selectedSlotStart, setSelectedSlotStart] = useState<string | null>(null)
  const [durationHours, setDurationHours] = useState(1)

  const availableSlots = data?.slots.filter((slot) => slot.status === 'AVAILABLE') ?? []

  const mutation = useMutation({
    mutationFn: (payload: { courtId: string; startAt: string; durationHours: number; notes?: string }) =>
      apiFetch<Reservation>('/api/v1/reservations', { method: 'POST', body: payload }, token ?? undefined),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
    },
  })

  const handleReserve = () => {
    if (!selectedSlotStart) return
    mutation.mutate({
      courtId: court.id,
      startAt: selectedSlotStart,
      durationHours,
      notes: `Reserva desde ${court.name}`,
    })
  }

  return (
    <article className="court-card court-card-modern">
      <div className="court-header">
        <div>
          <h3>{court.name}</h3>
          <p>{court.courtType}</p>
        </div>
        <span className={`status-tag ${court.active ? 'online' : 'offline'}`}>{court.active ? 'Activa' : 'Inactiva'}</span>
      </div>

      <p className="muted">{court.description || 'Sin descripción'}</p>

      {isLoading && <div className="status-box sub-box">Consultando disponibilidad...</div>}
      {isError && <div className="status-box error sub-box">{formatError(error)}</div>}

      {!isLoading && !isError && data && (
        <div className="availability-list">
          {availableSlots.length === 0 ? (
            <div className="status-box sub-box">No hay horarios disponibles para este día.</div>
          ) : (
            availableSlots.map((slot) => (
              <button
                key={`${slot.start}-${slot.end}`}
                type="button"
                className={`time-chip ${selectedSlotStart === slot.start ? 'selected' : ''}`}
                onClick={() => {
                  setSelectedSlotStart(slot.start)
                  setDurationHours(1)
                }}
              >
                {slot.start.slice(11, 16)} - {slot.end.slice(11, 16)} · {formatPrice(slot.price)}
              </button>
            ))
          )}
        </div>
      )}

      {isAuthenticated ? (
        <div className="booking-form">
          <div className="booking-summary">
            <span>Hora seleccionada</span>
            <strong>{selectedSlotStart ? formatDateTime(selectedSlotStart) : 'Sin elegir'}</strong>
          </div>

          <label htmlFor={`duration-${court.id}`}>Duración</label>
          <select id={`duration-${court.id}`} value={durationHours} onChange={(event) => setDurationHours(Number(event.target.value))}>
            {[1, 2, 3].map((hours) => (
              <option key={hours} value={hours}>
                {hours} hora{hours > 1 ? 's' : ''}
              </option>
            ))}
          </select>

          <button type="button" className="primary" onClick={handleReserve} disabled={!selectedSlotStart || mutation.isPending}>
            {mutation.isPending ? 'Reservando...' : 'Reservar'}
          </button>

          {mutation.isError && <div className="status-box error sub-box">{formatError(mutation.error)}</div>}
          {mutation.isSuccess && <div className="status-box success sub-box">Reserva creada correctamente.</div>}
        </div>
      ) : (
        <div className="status-box sub-box">Inicia sesión para reservar esta cancha.</div>
      )}
    </article>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useContext(AuthContext)
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}

function SuperAdminRoute({ children }: { children: React.ReactNode }) {
  const { user } = useContext(AuthContext)

  if (!user || !hasAnyRole(user, 'SUPER_ADMIN')) {
    return <div className="status-box error">Necesitas permisos de super administrador para acceder a esta vista.</div>
  }

  return <>{children}</>
}

function BackendRoutesPage() {
  const routeGroups = [
    { title: 'Autenticación', access: 'Público', items: ['POST /api/v1/auth/register', 'POST /api/v1/auth/login'] },
    { title: 'Usuarios', access: 'Autenticado', items: ['GET /api/v1/users/me', 'PATCH /api/v1/users/me', 'PUT /api/v1/users/me/password'] },
    { title: 'Sedes', access: 'Público + admin', items: ['GET /api/v1/venues', 'GET /api/v1/venues/{id}', 'POST /api/v1/venues', 'PUT /api/v1/venues/{venueId}', 'GET /api/v1/venues/mine'] },
    { title: 'Canchas', access: 'Público + admin', items: ['GET /api/v1/venues/{venueId}/courts', 'GET /api/v1/courts/{courtId}', 'POST /api/v1/venues/{venueId}/courts', 'PUT /api/v1/courts/{courtId}'] },
    { title: 'Reservas', access: 'Autenticado + admin', items: ['GET /api/v1/courts/{courtId}/availability', 'POST /api/v1/reservations', 'GET /api/v1/reservations/mine', 'GET /api/v1/reservations/{reservationId}', 'POST /api/v1/reservations/{reservationId}/cancel', 'POST /api/v1/reservations/{reservationId}/complete', 'POST /api/v1/reservations/{reservationId}/no-show', 'GET /api/v1/venues/{venueId}/reservations'] },
    { title: 'Horarios y bloqueos', access: 'Admin', items: ['GET /api/v1/courts/{courtId}/schedules', 'PUT /api/v1/courts/{courtId}/schedules', 'GET /api/v1/courts/{courtId}/blocks', 'POST /api/v1/courts/{courtId}/blocks', 'DELETE /api/v1/courts/{courtId}/blocks/{blockId}'] },
    { title: 'Reportes', access: 'Admin / Super admin', items: ['GET /api/v1/venues/{venueId}/reports/summary', 'GET /api/v1/venues/{venueId}/reports/daily', 'GET /api/v1/venues/{venueId}/reports/demand', 'GET /api/v1/admin/reports/summary'] },
    { title: 'Administración de usuarios', access: 'Super admin', items: ['GET /api/v1/admin/users', 'GET /api/v1/admin/users/{userId}', 'GET /api/v1/admin/users/{userId}/venues', 'PUT /api/v1/admin/users/{userId}/roles', 'PUT /api/v1/admin/users/{userId}/status'] },
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
        Esta vista deja explícito qué endpoints existen en la API y en qué pantallas del frontend quedan representados, para que no quede nada suelto.
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

function ReservationDetailPage() {
  const { reservationId } = useParams()
  const { token, user } = useContext(AuthContext)
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['reservation-detail', reservationId],
    queryFn: () => apiFetch<Reservation>(`/api/v1/reservations/${reservationId ?? ''}`, undefined, token ?? undefined),
    enabled: Boolean(reservationId),
  })

  const cancelMutation = useMutation({
    mutationFn: () => apiFetch<Reservation>(`/api/v1/reservations/${reservationId ?? ''}/cancel`, {
      method: 'POST',
      body: { reason: 'Cancelada desde la UI' },
    }, token ?? undefined),
    onSuccess: () => {
      refetch()
    },
  })

  if (!reservationId) return <div className="status-box error">Falta el identificador de la reserva.</div>
  if (isLoading) return <div className="status-box">Cargando detalle de la reserva...</div>
  if (isError) return <div className="status-box error">{formatError(error)}</div>
  if (!data) return <div className="status-box">No se encontró la reserva.</div>

  const reservation = data

  return (
    <section className="content-panel detail-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Reserva</p>
          <h1>Detalle de reserva</h1>
        </div>
        <Link to="/mis-reservas" className="secondary link-button">Volver a reservas</Link>
      </div>

      <div className="profile-card profile-layout">
        <div className="profile-hero">
          <div className="avatar-badge">R</div>
          <div>
            <h2>{reservation.court.name}</h2>
            <p>{reservation.court.venueName}</p>
          </div>
        </div>

        <div className="role-list">
          <span className={`status-tag ${reservation.status === 'CONFIRMED' ? 'online' : 'offline'}`}>{reservation.status}</span>
        </div>

        <div className="info-grid">
          <div className="info-chip">
            <p className="label">Inicio</p>
            <strong>{formatDateTime(reservation.startAt)}</strong>
          </div>
          <div className="info-chip">
            <p className="label">Fin</p>
            <strong>{formatDateTime(reservation.endAt)}</strong>
          </div>
          <div className="info-chip">
            <p className="label">Duración</p>
            <strong>{reservation.durationHours} hora{reservation.durationHours > 1 ? 's' : ''}</strong>
          </div>
          <div className="info-chip">
            <p className="label">Total</p>
            <strong>{formatPrice(reservation.totalPrice)}</strong>
          </div>
        </div>

        {reservation.notes && (
          <div className="info-chip">
            <p className="label">Notas</p>
            <strong>{reservation.notes}</strong>
          </div>
        )}

        {(user && hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')) || reservation.status === 'CONFIRMED' ? (
          <div className="venue-actions">
            <button type="button" className="secondary" onClick={() => cancelMutation.mutate()} disabled={cancelMutation.isPending || reservation.status !== 'CONFIRMED'}>
              {cancelMutation.isPending ? 'Cancelando...' : 'Cancelar reserva'}
            </button>
          </div>
        ) : null}

        {cancelMutation.isError && <div className="status-box error sub-box">{formatError(cancelMutation.error)}</div>}
        {cancelMutation.isSuccess && <div className="status-box success sub-box">Reserva cancelada correctamente.</div>}
      </div>
    </section>
  )
}

function CourtSchedulePage() {
  const { courtId } = useParams()
  const { token, user } = useContext(AuthContext)
  const [from, setFrom] = useState(() => getTodayIso())
  const [to, setTo] = useState(() => {
    const date = new Date()
    date.setDate(date.getDate() + 7)
    return date.toISOString().slice(0, 10)
  })

  const schedulesQuery = useQuery({
    queryKey: ['court-schedules', courtId],
    queryFn: () => apiFetch<Array<Record<string, unknown>>>(`/api/v1/courts/${courtId ?? ''}/schedules`, undefined, token ?? undefined),
    enabled: Boolean(courtId),
  })

  const blocksQuery = useQuery({
    queryKey: ['court-blocks', courtId, from, to],
    queryFn: () => apiFetch<Array<Record<string, unknown>>>(`/api/v1/courts/${courtId ?? ''}/blocks?from=${from}&to=${to}`, undefined, token ?? undefined),
    enabled: Boolean(courtId) && Boolean(token),
  })

  const [form, setForm] = useState({ startAt: '', endAt: '', reason: 'Bloqueo programado' })
  const mutation = useMutation({
    mutationFn: () => apiFetch<Record<string, unknown>>(`/api/v1/courts/${courtId ?? ''}/blocks`, { method: 'POST', body: form }, token ?? undefined),
    onSuccess: () => {
      setForm({ startAt: '', endAt: '', reason: 'Bloqueo programado' })
      queryClient.invalidateQueries({ queryKey: ['court-blocks'] })
    },
  })

  if (!courtId) return <div className="status-box error">Falta la cancha.</div>
  if (!user || !hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')) {
    return <div className="status-box error">Necesitas permisos de administración para gestionar horarios y bloqueos.</div>
  }

  return (
    <section className="content-panel routes-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Horarios</p>
          <h1>Agenda y bloqueos</h1>
        </div>
      </div>

      <div className="admin-grid">
        <div className="admin-card">
          <h3>Horario semanal</h3>
          {schedulesQuery.isLoading ? <div className="status-box">Cargando horario...</div> : null}
          {schedulesQuery.data && (
            <ul className="route-list">
              {schedulesQuery.data.map((slot, index) => (
                <li key={`${String(slot.dayOfWeek ?? index)}-${String(slot.startTime ?? 'slot')}`}>
                  {String(slot.dayOfWeek ?? index)} · {String(slot.startTime ?? 'N/A')} a {String(slot.endTime ?? 'N/A')} · ${String(slot.price ?? '')}
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="admin-card">
          <h3>Crear bloqueo</h3>
          <form className="stack-form" onSubmit={(event) => { event.preventDefault(); mutation.mutate() }}>
            <label>
              Inicio
              <input type="datetime-local" value={form.startAt} onChange={(event) => setForm((current) => ({ ...current, startAt: event.target.value }))} required />
            </label>
            <label>
              Fin
              <input type="datetime-local" value={form.endAt} onChange={(event) => setForm((current) => ({ ...current, endAt: event.target.value }))} required />
            </label>
            <label>
              Motivo
              <input value={form.reason} onChange={(event) => setForm((current) => ({ ...current, reason: event.target.value }))} required />
            </label>
            {mutation.isError && <div className="status-box error">{formatError(mutation.error)}</div>}
            {mutation.isSuccess && <div className="status-box success">Bloqueo creado.</div>}
            <button type="submit" className="primary" disabled={mutation.isPending}>Crear bloqueo</button>
          </form>
        </div>
      </div>

      <div className="admin-card">
        <div className="date-picker-wrap inline-range">
          <label>
            Desde
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </label>
          <label>
            Hasta
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </label>
        </div>

        {blocksQuery.isLoading && <div className="status-box">Cargando bloqueos...</div>}
        {blocksQuery.data && (
          <ul className="route-list">
            {blocksQuery.data.length === 0 ? <li>No hay bloqueos para este rango.</li> : blocksQuery.data.map((block) => (
              <li key={String(block.id ?? 'block')}>
                {String(block.reason ?? 'Bloqueo')} · {String(block.startAt ?? '')} → {String(block.endAt ?? '')}
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function VenueReportsPage() {
  const { venueId } = useParams()
  const { token, user } = useContext(AuthContext)
  const [from, setFrom] = useState(() => {
    const date = new Date()
    date.setDate(date.getDate() - 30)
    return date.toISOString().slice(0, 10)
  })
  const [to, setTo] = useState(() => getTodayIso())

  if (!user || !hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')) {
    return <div className="status-box error">Necesitas permisos de administrador para ver reportes.</div>
  }

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['venue-reports', venueId, from, to],
    queryFn: () => apiFetch<Record<string, unknown>>(`/api/v1/venues/${venueId ?? ''}/reports/summary?from=${from}&to=${to}`, undefined, token ?? undefined),
    enabled: Boolean(venueId),
  })

  return (
    <section className="content-panel routes-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Reportes</p>
          <h1>Resumen de sede</h1>
        </div>
      </div>

      <div className="date-picker-wrap inline-range">
        <label>
          Desde
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
      </div>

      {isLoading && <div className="status-box">Cargando reporte...</div>}
      {isError && <div className="status-box error">{formatError(error)}</div>}
      {data && (
        <div className="report-grid">
          {Object.entries(data).map(([key, value]) => (
            <div key={key} className="admin-card report-tile">
              <span>{key}</span>
              <strong>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</strong>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function PlatformReportsPage() {
  const { token, user } = useContext(AuthContext)
  const [from, setFrom] = useState(() => {
    const date = new Date()
    date.setDate(date.getDate() - 30)
    return date.toISOString().slice(0, 10)
  })
  const [to, setTo] = useState(() => getTodayIso())

  if (!user || !hasAnyRole(user, 'SUPER_ADMIN')) {
    return <div className="status-box error">Solo el super administrador puede ver este resumen global.</div>
  }

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['platform-reports', from, to],
    queryFn: () => apiFetch<Record<string, unknown>>(`/api/v1/admin/reports/summary?from=${from}&to=${to}`, undefined, token ?? undefined),
  })

  return (
    <section className="content-panel routes-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Plataforma</p>
          <h1>Resumen global</h1>
        </div>
      </div>

      <div className="date-picker-wrap inline-range">
        <label>
          Desde
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          Hasta
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
      </div>

      {isLoading && <div className="status-box">Cargando resumen...</div>}
      {isError && <div className="status-box error">{formatError(error)}</div>}
      {data && (
        <div className="report-grid">
          {Object.entries(data).map(([key, value]) => (
            <div key={key} className="admin-card report-tile">
              <span>{key}</span>
              <strong>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</strong>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

function AdminUsersPage() {
  const { token, user } = useContext(AuthContext)
  const [query, setQuery] = useState('')
  const [roleFilter, setRoleFilter] = useState<'ALL' | Role>('ALL')

  if (!user || !hasAnyRole(user, 'SUPER_ADMIN')) {
    return <div className="status-box error">Solo el super administrador puede administrar usuarios.</div>
  }

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin-users', query, roleFilter],
    queryFn: () => {
      const params = new URLSearchParams()
      if (query.trim()) params.set('q', query.trim())
      if (roleFilter !== 'ALL') params.set('role', roleFilter)
      return apiFetch<PageResponse<User>>(`/api/v1/admin/users?${params.toString()}`, undefined, token ?? undefined)
    },
  })

  const users = data?.content ?? []

  const setStatusMutation = useMutation({
    mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
      apiFetch<User>(`/api/v1/admin/users/${userId}/status`, { method: 'PUT', body: { enabled } }, token ?? undefined),
    onSuccess: () => refetch(),
  })

  const setRolesMutation = useMutation({
    mutationFn: ({ userId, roles }: { userId: string; roles: Role[] }) =>
      apiFetch<User>(`/api/v1/admin/users/${userId}/roles`, { method: 'PUT', body: { roles } }, token ?? undefined),
    onSuccess: () => refetch(),
  })

  return (
    <section className="content-panel routes-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Super admin</p>
          <h1>Usuarios y roles</h1>
        </div>
      </div>

      <div className="search-row compact">
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Buscar por nombre o email" />
      </div>

      <div className="date-picker-wrap inline-range">
        <label>
          Rol
          <select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value as 'ALL' | Role)}>
            <option value="ALL">Todos</option>
            <option value="CUSTOMER">CUSTOMER</option>
            <option value="ADMIN">ADMIN</option>
            <option value="SUPER_ADMIN">SUPER_ADMIN</option>
          </select>
        </label>
      </div>

      {isLoading && <div className="status-box">Cargando usuarios...</div>}
      {isError && <div className="status-box error">{formatError(error)}</div>}

      <div className="admin-list-card">
        {users.map((person) => (
          <div key={person.id} className="mini-item user-item">
            <div>
              <strong>{person.fullName}</strong>
              <p>{person.email}</p>
              <div className="role-list compact-role-list">
                {person.roles.map((role) => (
                  <span key={`${person.id}-${role}`} className="role-pill">{role}</span>
                ))}
              </div>
            </div>
            <div className="user-actions">
              <button type="button" className="secondary small-button" onClick={() => setStatusMutation.mutate({ userId: person.id, enabled: !person.enabled })} disabled={setStatusMutation.isPending}>
                {person.enabled ? 'Desactivar' : 'Activar'}
              </button>
              <button type="button" className="primary small-button" onClick={() => setRolesMutation.mutate({ userId: person.id, roles: ['CUSTOMER', 'ADMIN'] })} disabled={setRolesMutation.isPending}>
                Dar ADMIN
              </button>
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

function ReservationsPage() {
  const { token } = useContext(AuthContext)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['reservations'],
    queryFn: () => apiFetch<PageResponse<Reservation>>('/api/v1/reservations/mine?upcoming=false', undefined, token ?? undefined),
  })

  const reservations = data?.content ?? []

  return (
    <section className="content-panel">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Mi cuenta</p>
          <h1>Mis reservas</h1>
        </div>
      </div>

      {isLoading && <div className="status-box">Cargando reservas...</div>}
      {isError && <div className="status-box error">{formatError(error)}</div>}

      {!isLoading && !isError && reservations.length === 0 && <div className="status-box">Aún no tienes reservas.</div>}

      <div className="reservation-stack">
        {reservations.map((reservation) => (
          <article key={reservation.id} className="reservation-card">
            <div className="reservation-topline">
              <span className={`status-tag ${reservation.status === 'CONFIRMED' ? 'online' : 'offline'}`}>{reservation.status}</span>
              <strong>{formatPrice(reservation.totalPrice)}</strong>
            </div>
            <h3>{reservation.court.name}</h3>
            <p>{reservation.court.venueName}</p>
            <p>{formatDateTime(reservation.startAt)} - {formatDateTime(reservation.endAt)}</p>
            {reservation.notes && <p className="muted">Notas: {reservation.notes}</p>}
          </article>
        ))}
      </div>
    </section>
  )
}

function MyVenuesPage() {
  const { token, user } = useContext(AuthContext)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['my-venues'],
    queryFn: () => apiFetch<PageResponse<Venue>>('/api/v1/venues/mine', undefined, token ?? undefined),
  })

  const venues = data?.content ?? []
  const canManage = hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')

  return (
    <section className="content-panel">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Administración</p>
          <h1>Mis sedes</h1>
        </div>
      </div>

      {!canManage && <div className="status-box error">Necesitas permisos de administrador para gestionar sedes.</div>}

      {isLoading && <div className="status-box">Cargando sedes propias...</div>}
      {isError && <div className="status-box error">{formatError(error)}</div>}
      {!isLoading && !isError && venues.length === 0 && <div className="status-box">Todavía no administras sedes.</div>}

      <div className="card-grid">
        {venues.map((venue) => (
          <article key={venue.id} className="venue-card">
            <h3>{venue.name}</h3>
            <p>{venue.address}</p>
            <p className="muted">{venue.neighborhood || 'Sin barrio'}</p>
            <div className="venue-actions">
              <span className={`status-tag ${venue.active ? 'online' : 'offline'}`}>{venue.active ? 'Activa' : 'Inactiva'}</span>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function ProfilePage() {
  const { token, user } = useContext(AuthContext)
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['profile'],
    queryFn: () => apiFetch<User>('/api/v1/users/me', undefined, token ?? undefined),
  })

  const profile = data ?? user

  return (
    <section className="content-panel profile-page">
      <div className="heading-row">
        <div>
          <p className="eyebrow">Cuenta</p>
          <h1>Mi perfil</h1>
        </div>
      </div>

      {isLoading && <div className="status-box">Cargando perfil...</div>}
      {isError && <div className="status-box error">{formatError(error)}</div>}

      {profile && (
        <div className="profile-card profile-layout">
          <div className="profile-hero">
            <div className="avatar-badge">{profile.fullName.charAt(0).toUpperCase()}</div>
            <div>
              <h2>{profile.fullName}</h2>
              <p>{profile.email}</p>
            </div>
          </div>

          <div className="role-list">
            {profile.roles.map((role) => (
              <span key={role} className="role-pill">
                {role}
              </span>
            ))}
          </div>

          <div className="info-grid">
            <div className="info-chip">
              <p className="label">Estado</p>
              <strong>{profile.enabled ? 'Activo' : 'Inactivo'}</strong>
            </div>
            <div className="info-chip">
              <p className="label">Fecha de registro</p>
              <strong>{formatDateTime(profile.createdAt)}</strong>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

function AdminDashboardPage() {
  const { token, user } = useContext(AuthContext)

  if (!user || !hasAnyRole(user, 'ADMIN', 'SUPER_ADMIN')) {
    return <div className="status-box error">No tienes permisos suficientes para acceder al panel de administración.</div>
  }

  const venuesQuery = useQuery({
    queryKey: ['my-venues-admin'],
    queryFn: () => apiFetch<PageResponse<Venue>>('/api/v1/venues/mine', undefined, token ?? undefined),
  })

  const venues = venuesQuery.data?.content ?? []
  const activeVenues = venues.filter((venue) => venue.active).length
  const estimatedRevenue = venues.length * 2100000

  return (
    <section className="admin-dashboard-shell">
      <div className="admin-hero">
        <div>
          <p className="eyebrow warm">Administración</p>
          <h1>Panel de control</h1>
        </div>
        <button type="button" className="primary cta-button small-button">
          Nueva operación
        </button>
      </div>

      <div className="admin-summary-grid">
        <div className="summary-tile soft-accent">
          <span>Sedes activas</span>
          <strong>{activeVenues}</strong>
          <small>operando hoy</small>
        </div>
        <div className="summary-tile">
          <span>Reservas hoy</span>
          <strong>48</strong>
          <small>12 últimas horas</small>
        </div>
        <div className="summary-tile">
          <span>Ingreso estimado</span>
          <strong>{formatPrice(estimatedRevenue)}</strong>
          <small>proyección del día</small>
        </div>
      </div>

      <div className="admin-grid">
        <div className="admin-card admin-form-panel">
          <div className="panel-header compact-header">
            <div>
              <p className="eyebrow">Gestión</p>
              <h2>Crear sede</h2>
            </div>
          </div>
          <VenueForm token={token ?? undefined} />
        </div>

        <div className="admin-card admin-form-panel">
          <div className="panel-header compact-header">
            <div>
              <p className="eyebrow">Gestión</p>
              <h2>Crear cancha</h2>
            </div>
          </div>
          <CourtForm venues={venues} token={token ?? undefined} />
        </div>
      </div>

      <div className="admin-lower-grid">
        <div className="admin-card admin-list-card">
          <div className="panel-header compact-header">
            <div>
              <p className="eyebrow">Cobertura</p>
              <h2>Mis sedes</h2>
            </div>
          </div>

          {venuesQuery.isLoading && <div className="status-box">Cargando sedes...</div>}
          {venuesQuery.isError && <div className="status-box error">{formatError(venuesQuery.error)}</div>}
          {!venuesQuery.isLoading && !venuesQuery.isError && venues.length === 0 && <div className="status-box">Todavía no tienes sedes creadas.</div>}

          <div className="mini-list">
            {venues.map((venue) => (
              <div key={venue.id} className="mini-item">
                <div>
                  <strong>{venue.name}</strong>
                  <p>{venue.address}</p>
                </div>
                <span className={`status-tag ${venue.active ? 'online' : 'offline'}`}>{venue.active ? 'Activa' : 'Inactiva'}</span>
              </div>
            ))}
          </div>
        </div>

        <div className="admin-card admin-list-card">
          <div className="panel-header compact-header">
            <div>
              <p className="eyebrow">Acciones</p>
              <h2>Rápidas</h2>
            </div>
          </div>

          <div className="quick-actions">
            <button type="button" className="secondary quick-action">Ver agenda</button>
            <button type="button" className="secondary quick-action">Editar horarios</button>
            <button type="button" className="secondary quick-action">Reservas pendientes</button>
            <button type="button" className="secondary quick-action">Reportes</button>
          </div>
        </div>
      </div>
    </section>
  )
}

function VenueForm({ token }: { token?: string }) {
  const [form, setForm] = useState({
    name: '',
    address: '',
    neighborhood: '',
    phone: '',
    description: '',
    active: true,
  })
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch<Venue>('/api/v1/venues', { method: 'POST', body: form }, token),
    onSuccess: () => {
      setStatus('Sede creada correctamente.')
      setError(null)
      setForm({
        name: '',
        address: '',
        neighborhood: '',
        phone: '',
        description: '',
        active: true,
      })
      queryClient.invalidateQueries({ queryKey: ['my-venues'] })
      queryClient.invalidateQueries({ queryKey: ['my-venues-admin'] })
    },
    onError: (caughtError) => {
      setError(formatError(caughtError))
      setStatus(null)
    },
  })

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <form className="stack-form" onSubmit={handleSubmit}>
      <label>
        Nombre
        <input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} required />
      </label>
      <label>
        Dirección
        <input value={form.address} onChange={(event) => setForm((current) => ({ ...current, address: event.target.value }))} required />
      </label>
      <label>
        Barrio
        <input value={form.neighborhood} onChange={(event) => setForm((current) => ({ ...current, neighborhood: event.target.value }))} />
      </label>
      <label>
        Teléfono
        <input value={form.phone} onChange={(event) => setForm((current) => ({ ...current, phone: event.target.value }))} />
      </label>
      <label>
        Descripción
        <textarea value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} rows={4} />
      </label>
      <label className="inline-toggle">
        <input type="checkbox" checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} />
        Sede activa
      </label>

      {error && <div className="status-box error">{error}</div>}
      {status && <div className="status-box success">{status}</div>}

      <button type="submit" className="primary" disabled={mutation.isPending}>
        {mutation.isPending ? 'Guardando...' : 'Crear sede'}
      </button>
    </form>
  )
}

function CourtForm({ venues, token }: { venues: Venue[]; token?: string }) {
  const [selectedVenueId, setSelectedVenueId] = useState(venues[0]?.id ?? '')
  const [form, setForm] = useState({
    name: '',
    courtType: 'FUTBOL_5',
    description: '',
    active: true,
  })
  const [status, setStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useState(() => {
    if (venues.length > 0 && !selectedVenueId) {
      setSelectedVenueId(venues[0].id)
    }
  })

  const mutation = useMutation({
    mutationFn: () =>
      apiFetch<Court>(`/api/v1/venues/${selectedVenueId}/courts`, { method: 'POST', body: form }, token),
    onSuccess: () => {
      setStatus('Cancha creada correctamente.')
      setError(null)
      setForm({
        name: '',
        courtType: 'FUTBOL_5',
        description: '',
        active: true,
      })
      queryClient.invalidateQueries({ queryKey: ['courts'] })
    },
    onError: (caughtError) => {
      setError(formatError(caughtError))
      setStatus(null)
    },
  })

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedVenueId) {
      setError('Primero crea una sede para poder asociar una cancha.')
      return
    }
    mutation.mutate()
  }

  return (
    <form className="stack-form" onSubmit={handleSubmit}>
      <label>
        Sede
        <select value={selectedVenueId} onChange={(event) => setSelectedVenueId(event.target.value)}>
          {venues.length === 0 ? <option value="">Sin sedes</option> : venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
        </select>
      </label>
      <label>
        Nombre de cancha
        <input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} required />
      </label>
      <label>
        Tipo
        <select value={form.courtType} onChange={(event) => setForm((current) => ({ ...current, courtType: event.target.value }))}>
          {['FUTBOL_5', 'FUTBOL_6', 'FUTBOL_7', 'FUTBOL_8', 'FUTBOL_11'].map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>
      <label>
        Descripción
        <textarea value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} rows={4} />
      </label>
      <label className="inline-toggle">
        <input type="checkbox" checked={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.checked }))} />
        Cancha activa
      </label>

      {error && <div className="status-box error">{error}</div>}
      {status && <div className="status-box success">{status}</div>}

      <button type="submit" className="primary" disabled={mutation.isPending || !selectedVenueId}>
        {mutation.isPending ? 'Guardando...' : 'Crear cancha'}
      </button>
    </form>
  )
}

function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register, isAuthenticated } = useContext(AuthContext)
  const navigate = useNavigate()
  const location = useLocation()

  const [values, setValues] = useState<AuthFormValues>({
    email: '',
    password: '',
    fullName: '',
  })
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (isAuthenticated) {
    const fromPath = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={fromPath} replace />
  }

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      if (mode === 'login') {
        await login({ email: values.email, password: values.password })
      } else {
        await register({ fullName: values.fullName, email: values.email, password: values.password })
      }
      navigate('/')
    } catch (caughtError) {
      setError(formatError(caughtError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-shell">
      <div className="auth-visual-panel">
        <div className="auth-visual-inner">
          <p className="eyebrow warm">Bienvenido</p>
          <h2>Organiza tus partidos con estilo.</h2>
          <ul>
            <li>Reserva canchas en segundos</li>
            <li>Consulta disponibilidad real</li>
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
        <p className="eyebrow">{mode === 'login' ? 'Ingresar' : 'Registro'}</p>
        <h1>{mode === 'login' ? 'Iniciar sesión' : 'Crear cuenta'}</h1>
        <form onSubmit={submit} className="auth-form">
          {mode === 'register' && (
            <label>
              Nombre completo
              <input
                type="text"
                value={values.fullName}
                onChange={(event) => setValues((current) => ({ ...current, fullName: event.target.value }))}
                placeholder="Tu nombre"
                required
              />
            </label>
          )}

          <label>
            Email
            <input
              type="email"
              value={values.email}
              onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))}
              placeholder="correo@ejemplo.com"
              required
            />
          </label>

          <label>
            Contraseña
            <input
              type="password"
              value={values.password}
              onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
              placeholder="••••••••"
              required
            />
          </label>

          {error && <div className="status-box error">{error}</div>}

          <button type="submit" className="primary" disabled={submitting}>
            {submitting ? 'Procesando...' : mode === 'login' ? 'Entrar' : 'Registrarme'}
          </button>
        </form>

        <p className="switch-text">
          {mode === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?'}{' '}
          <Link to={mode === 'login' ? '/register' : '/login'}>{mode === 'login' ? 'Regístrate aquí' : 'Inicia sesión'}</Link>
        </p>
      </div>
    </section>
  )
}

type ApiRequestOptions = Omit<RequestInit, 'body'> & {
  body?: BodyInit | Record<string, unknown> | unknown[] | null
}

async function apiFetch<T>(path: string, options: ApiRequestOptions = {}, token?: string): Promise<T> {
  const { body, ...requestOptions } = options
  const headers = new Headers(requestOptions.headers ?? {})
  const requestInit: RequestInit = { ...requestOptions, headers }

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  if (body !== undefined && body !== null && typeof body !== 'string' && !(body instanceof FormData) && !(body instanceof URLSearchParams) && !(body instanceof Blob) && !(body instanceof ArrayBuffer) && !(ArrayBuffer.isView(body))) {
    requestInit.body = JSON.stringify(body)
    if (!headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
  } else if (body !== undefined && body !== null) {
    requestInit.body = body as BodyInit
  }

  const response = await fetch(`${API_BASE}${path}`, requestInit)
  let payload: unknown = null

  const text = await response.text()
  if (text) {
    try {
      payload = JSON.parse(text)
    } catch {
      payload = text
    }
  }

  if (!response.ok) {
    const errorPayload = payload as ErrorPayload | null
    const errorMessage = errorPayload?.code ? `${errorPayload.code}: ${errorPayload.detail ?? 'Error del servidor'}` : 'Error al consultar la API'
    throw new Error(errorMessage)
  }

  return payload as T
}

function formatError(error: unknown): string {
  if (error instanceof Error) return error.message
  if (typeof error === 'string') return error
  return 'No se pudo completar la operación.'
}

function formatPrice(value: number): string {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(value)
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('es-CO', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function getTodayIso() {
  const now = new Date()
  const offset = now.getTimezoneOffset()
  const local = new Date(now.getTime() - offset * 60 * 1000)
  return local.toISOString().slice(0, 10)
}

export default App
