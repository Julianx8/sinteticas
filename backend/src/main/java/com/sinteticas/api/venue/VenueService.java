package com.sinteticas.api.venue;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.court.CourtType;
import com.sinteticas.api.holiday.HolidayCalendar;
import com.sinteticas.api.reservation.ReservationProperties;
import com.sinteticas.api.venue.dto.VenueRequest;
import org.springframework.data.domain.Page;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final BusinessTime time;
    private final HolidayCalendar holidayCalendar;
    private final ReservationProperties reservationProperties;

    public VenueService(VenueRepository venueRepository, BusinessTime time, HolidayCalendar holidayCalendar,
                        ReservationProperties reservationProperties) {
        this.venueRepository = venueRepository;
        this.time = time;
        this.holidayCalendar = holidayCalendar;
        this.reservationProperties = reservationProperties;
    }

    @Transactional(readOnly = true)
    public Page<Venue> searchActive(String q, String neighborhood, CourtType courtType, Pageable pageable) {
        return venueRepository.searchActive(blankToNull(q), blankToNull(neighborhood), courtType, pageable);
    }

    /**
     * Catálogo filtrado además por disponibilidad: sedes con alguna cancha libre el día {@code date}
     * de {@code hour} a {@code hour + 1} (hora de Colombia).
     */
    @Transactional(readOnly = true)
    public Page<Venue> searchActiveFreeAt(String q, String neighborhood, CourtType courtType,
                                          LocalDate date, int hour, Pageable pageable) {
        if (hour < 0 || hour > 23) {
            throw ApiException.badRequest("INVALID_HOUR", "La hora debe estar entre 0 y 23");
        }
        if (date.isAfter(time.today().plusDays(reservationProperties.maxDaysAhead()))) {
            throw ApiException.businessRule("TOO_FAR_AHEAD",
                    "Solo se puede buscar hasta " + reservationProperties.maxDaysAhead() + " días adelante");
        }
        Instant start = time.atHour(date, hour);
        if (!start.isAfter(time.now())) {
            throw ApiException.businessRule("RESERVATION_IN_PAST", "Esa hora ya pasó");
        }
        return venueRepository.searchActiveFreeAt(blankToNull(q), blankToNull(neighborhood), courtType,
                holidayCalendar.scheduleDayOfWeek(date), hour, start, time.atHour(date, hour + 1), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Venue> listOwnedBy(UUID ownerId, Pageable pageable) {
        return venueRepository.findByOwnerId(ownerId, pageable);
    }

    /**
     * Sede visible para quien consulta: las inactivas solo las ve quien la administra.
     * Para el resto se responde 404 (no revelamos que existe).
     */
    @Transactional(readOnly = true)
    public Venue getVisible(UUID venueId, Optional<AuthenticatedUser> viewer) {
        Venue venue = find(venueId);
        boolean canManage = viewer.map(venue::canBeManagedBy).orElse(false);
        if (!venue.isActive() && !canManage) {
            throw venueNotFound();
        }
        return venue;
    }

    /** Sede que el usuario puede modificar (dueño o SUPER_ADMIN); si no, 403. */
    @Transactional(readOnly = true)
    public Venue getManageable(UUID venueId, AuthenticatedUser user) {
        Venue venue = find(venueId);
        if (!venue.canBeManagedBy(user)) {
            throw ApiException.forbidden("VENUE_NOT_OWNED", "No administras esta sede");
        }
        return venue;
    }

    @Transactional
    public Venue create(AuthenticatedUser owner, VenueRequest request) {
        Venue venue = new Venue(owner.id());
        apply(venue, request);
        return venueRepository.saveAndFlush(venue);
    }

    @Transactional
    public Venue update(AuthenticatedUser user, UUID venueId, VenueRequest request) {
        Venue venue = getManageable(venueId, user);
        apply(venue, request);
        return venue;
    }

    private void apply(Venue venue, VenueRequest request) {
        venue.setName(request.name());
        venue.setAddress(request.address());
        venue.setNeighborhood(blankToNull(request.neighborhood()));
        venue.setPhone(blankToNull(request.phone()));
        venue.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            venue.setActive(request.active());
        }
    }

    private Venue find(UUID venueId) {
        return venueRepository.findById(venueId).orElseThrow(VenueService::venueNotFound);
    }

    private static ApiException venueNotFound() {
        return ApiException.notFound("VENUE_NOT_FOUND", "Sede no encontrada");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
