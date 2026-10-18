package com.sinteticas.api.reservation;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.court.CourtService;
import com.sinteticas.api.reservation.dto.CreateReservationRequest;
import com.sinteticas.api.reservation.dto.ReservationResponse;
import com.sinteticas.api.schedule.BlockService;
import com.sinteticas.api.schedule.ScheduleService;
import com.sinteticas.api.user.User;
import com.sinteticas.api.user.UserService;
import com.sinteticas.api.venue.VenueService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reglas de negocio de las reservas. Las fechas se interpretan en hora de Colombia.
 */
@Service
public class ReservationService {

    /** Nombre de la restricción EXCLUDE de V5: la última defensa contra reservas que se cruzan. */
    private static final String NO_OVERLAP_CONSTRAINT = "ex_reservations_no_overlap";

    /** Los listados de la sede abarcan como máximo ~3 meses. */
    private static final Duration MAX_VENUE_RANGE = Duration.ofDays(93);

    private final ReservationRepository reservationRepository;
    private final CourtService courtService;
    private final VenueService venueService;
    private final ScheduleService scheduleService;
    private final BlockService blockService;
    private final UserService userService;
    private final BusinessTime time;
    private final ReservationProperties properties;
    private final ApplicationEventPublisher events;

    public ReservationService(ReservationRepository reservationRepository, CourtService courtService,
                              VenueService venueService, ScheduleService scheduleService,
                              BlockService blockService, UserService userService, BusinessTime time,
                              ReservationProperties properties, ApplicationEventPublisher events) {
        this.reservationRepository = reservationRepository;
        this.courtService = courtService;
        this.venueService = venueService;
        this.scheduleService = scheduleService;
        this.blockService = blockService;
        this.userService = userService;
        this.time = time;
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public ReservationResponse create(AuthenticatedUser user, CreateReservationRequest request) {
        Court court = courtService.getVisible(request.courtId(), Optional.of(user));
        if (!court.isBookable()) {
            throw ApiException.businessRule("COURT_NOT_BOOKABLE", "La cancha no está disponible para reservas");
        }

        ZonedDateTime start = time.toZoned(request.startAt().toInstant());
        int hours = request.durationHours();
        validateTiming(start, hours);

        Instant startAt = start.toInstant();
        Instant endAt = start.plusHours(hours).toInstant();
        BigDecimal totalPrice = priceFor(court.getId(), start.toLocalDate(), start.getHour(), hours);

        if (!blockService.findOverlapping(court.getId(), startAt, endAt).isEmpty()) {
            throw ApiException.conflict("SLOT_BLOCKED", "La sede bloqueó ese horario");
        }
        // Chequeo previo para dar un mensaje claro; la garantía real es la restricción de la base de datos
        if (!reservationRepository.findOccupying(court.getId(), startAt, endAt).isEmpty()) {
            throw slotTaken();
        }
        if (reservationRepository.countUpcomingConfirmed(user.id(), time.now()) >= properties.maxActivePerCustomer()) {
            throw ApiException.businessRule("TOO_MANY_ACTIVE_RESERVATIONS",
                    "Tienes el máximo de " + properties.maxActivePerCustomer() + " reservas activas");
        }

        User customer = userService.getById(user.id());
        Reservation reservation = new Reservation(court, customer, startAt, endAt, totalPrice,
                blankToNull(request.notes()));
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            // Otra petición reservó el mismo horario entre nuestro chequeo y el INSERT
            if (isNoOverlapViolation(ex)) {
                throw slotTaken();
            }
            throw ex;
        }
        ReservationResponse response = ReservationResponse.from(reservation, time);
        events.publishEvent(new ReservationEvents.Created(response, court.getVenue().getOwnerId()));
        return response;
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(AuthenticatedUser user, UUID reservationId) {
        Reservation reservation = findAccessible(user, reservationId);
        return ReservationResponse.from(reservation, time);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> listMine(AuthenticatedUser user, ReservationStatus status, boolean upcomingOnly,
                                              Pageable pageable) {
        // Sin filtro se usa EPOCH (no null): PostgreSQL no puede inferir el tipo de un parámetro de fecha nulo
        Instant from = upcomingOnly ? time.now() : Instant.EPOCH;
        return reservationRepository.findForCustomer(user.id(), status, from, pageable)
                .map(r -> ReservationResponse.from(r, time));
    }

    /** Agenda de la sede (solo dueño o SUPER_ADMIN), entre dos fechas inclusive. */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> listForVenue(AuthenticatedUser user, UUID venueId, LocalDate from, LocalDate to,
                                                  ReservationStatus status, UUID courtId, Pageable pageable) {
        venueService.getManageable(venueId, user);
        Instant fromInstant = time.atHour(from, 0);
        Instant toInstant = time.atHour(to, 24);
        if (!toInstant.isAfter(fromInstant)) {
            throw ApiException.badRequest("INVALID_RANGE", "La fecha final debe ser igual o posterior a la inicial");
        }
        if (Duration.between(fromInstant, toInstant).compareTo(MAX_VENUE_RANGE) > 0) {
            throw ApiException.badRequest("RANGE_TOO_LARGE", "El rango de consulta no puede superar 3 meses");
        }
        return reservationRepository.findForVenue(venueId, fromInstant, toInstant, status, courtId, pageable)
                .map(r -> ReservationResponse.from(r, time));
    }

    /**
     * Cancelar: el cliente hasta {@code customer-cancellation-notice} antes del inicio;
     * la sede en cualquier momento antes del inicio.
     */
    @Transactional
    public ReservationResponse cancel(AuthenticatedUser user, UUID reservationId, String reason) {
        Reservation reservation = findAccessible(user, reservationId);
        Instant now = time.now();
        boolean byVenue = reservation.getCourt().canBeManagedBy(user);

        if (reservation.hasStarted(now)) {
            throw ApiException.businessRule("RESERVATION_ALREADY_STARTED",
                    "No se puede cancelar una reserva que ya empezó");
        }
        if (!byVenue) {
            Duration notice = properties.customerCancellationNotice();
            if (Duration.between(now, reservation.getStartAt()).compareTo(notice) < 0) {
                throw ApiException.businessRule("CANCELLATION_TOO_LATE",
                        "Solo puedes cancelar hasta " + notice.toHours() + " horas antes del inicio");
            }
        }
        reservation.cancel(user.id(), blankToNull(reason), now);
        ReservationResponse response = ReservationResponse.from(reservation, time);
        events.publishEvent(new ReservationEvents.Cancelled(response,
                reservation.getCourt().getVenue().getOwnerId(), byVenue));
        return response;
    }

    /** La sede registra si la reserva se jugó (COMPLETED) o si el cliente no llegó (NO_SHOW). */
    @Transactional
    public ReservationResponse markOutcome(AuthenticatedUser user, UUID reservationId, ReservationStatus outcome) {
        Reservation reservation = findAccessible(user, reservationId);
        if (!reservation.getCourt().canBeManagedBy(user)) {
            throw ApiException.forbidden("COURT_NOT_OWNED", "Solo la sede puede registrar el resultado");
        }
        reservation.markOutcome(outcome, time.now());
        return ReservationResponse.from(reservation, time);
    }

    private void validateTiming(ZonedDateTime start, int hours) {
        if (start.getMinute() != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw ApiException.badRequest("INVALID_START_TIME", "Las reservas empiezan en punto (p. ej. 18:00)");
        }
        if (hours > properties.maxHours()) {
            throw ApiException.businessRule("DURATION_TOO_LONG",
                    "Una reserva puede durar máximo " + properties.maxHours() + " horas");
        }
        if (start.getHour() + hours > 24) {
            throw ApiException.businessRule("CROSSES_MIDNIGHT", "La reserva debe terminar a más tardar a la medianoche");
        }
        if (!start.toInstant().isAfter(time.now())) {
            throw ApiException.businessRule("RESERVATION_IN_PAST", "No se puede reservar en una hora que ya pasó");
        }
        if (start.toLocalDate().isAfter(time.today().plusDays(properties.maxDaysAhead()))) {
            throw ApiException.businessRule("TOO_FAR_AHEAD",
                    "Solo se puede reservar con " + properties.maxDaysAhead() + " días de anticipación");
        }
    }

    /** Suma el precio de cada hora; si alguna hora está fuera del horario de la cancha, no se puede reservar. */
    private BigDecimal priceFor(UUID courtId, LocalDate date, int startHour, int hours) {
        Map<Integer, BigDecimal> prices = scheduleService.hourlyPrices(courtId, date);
        BigDecimal total = BigDecimal.ZERO;
        for (int hour = startHour; hour < startHour + hours; hour++) {
            BigDecimal price = prices.get(hour);
            if (price == null) {
                throw ApiException.businessRule("OUTSIDE_SCHEDULE", "La cancha no abre en ese horario");
            }
            total = total.add(price);
        }
        return total;
    }

    /**
     * Reserva que el usuario puede ver: el cliente que la hizo o quien administra la sede.
     * A cualquier otro se le responde 404 (no revelamos que existe).
     */
    private Reservation findAccessible(AuthenticatedUser user, UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(ReservationService::reservationNotFound);
        if (!reservation.isCustomer(user.id()) && !reservation.getCourt().canBeManagedBy(user)) {
            throw reservationNotFound();
        }
        return reservation;
    }

    private static boolean isNoOverlapViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(NO_OVERLAP_CONSTRAINT);
    }

    private static ApiException slotTaken() {
        return ApiException.conflict("RESERVATION_SLOT_TAKEN", "Ese horario ya está reservado");
    }

    private static ApiException reservationNotFound() {
        return ApiException.notFound("RESERVATION_NOT_FOUND", "Reserva no encontrada");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
