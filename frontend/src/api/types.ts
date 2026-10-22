// Tipos de la API. Deben coincidir con los DTO del backend (paquetes */dto).
// Si cambia un DTO, se actualiza aquí.

export type Role = 'CUSTOMER' | 'ADMIN' | 'SUPER_ADMIN'

export type User = {
  id: string
  fullName: string
  email: string
  roles: Role[]
  enabled: boolean
  createdAt: string
}

export type AuthResponse = {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  user: User
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Venue = {
  id: string
  ownerId: string
  name: string
  address: string
  neighborhood: string | null
  phone: string | null
  description: string | null
  active: boolean
  createdAt: string
}

export type VenueInput = {
  name: string
  address: string
  neighborhood: string
  phone: string
  description: string
  active: boolean
}

export const COURT_TYPES = ['FUTBOL_5', 'FUTBOL_6', 'FUTBOL_7', 'FUTBOL_8', 'FUTBOL_11'] as const
export type CourtType = (typeof COURT_TYPES)[number]

export type Court = {
  id: string
  venueId: string
  venueName: string
  name: string
  courtType: CourtType
  description: string | null
  active: boolean
}

export type CourtInput = {
  name: string
  courtType: CourtType
  description: string
  active: boolean
}

export type ReservationStatus = 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'

export type AvailabilityStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED' | 'PAST'

export type AvailabilitySlot = {
  hour: number
  startAt: string
  endAt: string
  price: number
  status: AvailabilityStatus
}

export type AvailabilityResponse = {
  date: string
  courtId: string
  // Solo viene cuando la fecha es festivo
  holidayName?: string
  slots: AvailabilitySlot[]
}

/** dayOfWeek: 1 = lunes … 7 = domingo. endHour es exclusiva (24 = medianoche). */
export type ScheduleSlot = {
  dayOfWeek: number
  startHour: number
  endHour: number
  pricePerHour: number
}

export type Block = {
  id: string
  courtId: string
  startAt: string
  endAt: string
  reason: string
}

export type Reservation = {
  id: string
  status: ReservationStatus
  startAt: string
  endAt: string
  durationHours: number
  totalPrice: number
  notes: string | null
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

/** Tasas de 0 a 1 (0.25 = 25 %). availableHours y occupancyRate no vienen en el resumen de plataforma. */
export type Metrics = {
  reservations: number
  confirmed: number
  completed: number
  cancelled: number
  noShow: number
  bookedHours: number
  availableHours?: number
  occupancyRate?: number
  realizedRevenue: number
  projectedRevenue: number
  cancellationRate: number
  noShowRate: number
}

export type VenueSummaryReport = {
  venueId: string
  venueName: string
  from: string
  to: string
  totals: Metrics
  courts: { courtId: string; courtName: string; active: boolean; metrics: Metrics }[]
}

export type VenueDailyReport = {
  venueId: string
  from: string
  to: string
  days: { date: string; metrics: Metrics }[]
}

export type DemandCell = { dayOfWeek: number; hour: number; bookedHours: number }

export type VenueDemandReport = {
  venueId: string
  from: string
  to: string
  cells: DemandCell[]
  peakHours: DemandCell[]
}

export type PlatformSummaryReport = {
  from: string
  to: string
  totalUsers: number
  newUsers: number
  activeVenues: number
  activeCourts: number
  reservations: Metrics
  months: { month: string; reservations: number; cancelled: number; realizedRevenue: number; projectedRevenue: number }[]
  topVenues: { venueId: string; venueName: string; reservations: number; realizedRevenue: number }[]
}

export type Holiday = { date: string; name: string }
