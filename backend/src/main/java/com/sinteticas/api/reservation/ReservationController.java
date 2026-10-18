package com.sinteticas.api.reservation;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.web.PageResponse;
import com.sinteticas.api.common.web.Pageables;
import com.sinteticas.api.reservation.dto.AvailabilityResponse;
import com.sinteticas.api.reservation.dto.CancelReservationRequest;
import com.sinteticas.api.reservation.dto.CreateReservationRequest;
import com.sinteticas.api.reservation.dto.ReservationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reservas")
public class ReservationController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("startAt", "createdAt");

    private final ReservationService reservationService;
    private final AvailabilityService availabilityService;

    public ReservationController(ReservationService reservationService, AvailabilityService availabilityService) {
        this.reservationService = reservationService;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/courts/{courtId}/availability")
    @Operation(summary = "Horas disponibles de una cancha en un día (público)",
            description = "Una entrada por cada hora en que la cancha abre, con su precio y estado")
    public AvailabilityResponse availability(@PathVariable UUID courtId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @AuthenticationPrincipal Jwt jwt) {
        return availabilityService.forDay(courtId, date, AuthenticatedUser.fromNullable(jwt));
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reservar una cancha")
    public ReservationResponse create(AuthenticatedUser currentUser,
                                      @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(currentUser, request);
    }

    @GetMapping("/reservations/mine")
    @Operation(summary = "Mis reservas (upcoming=true para ver solo las próximas)")
    public PageResponse<ReservationResponse> mine(AuthenticatedUser currentUser,
                                                  @RequestParam(required = false) ReservationStatus status,
                                                  @RequestParam(defaultValue = "false") boolean upcoming,
                                                  @PageableDefault(size = 20, sort = "startAt",
                                                          direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable safePageable = Pageables.restrict(pageable, SORTABLE_FIELDS);
        return toPage(reservationService.listMine(currentUser, status, upcoming, safePageable));
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(summary = "Detalle de una reserva (el cliente que la hizo o la sede)")
    public ReservationResponse get(AuthenticatedUser currentUser, @PathVariable UUID reservationId) {
        return reservationService.get(currentUser, reservationId);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    @Operation(summary = "Cancelar una reserva")
    public ReservationResponse cancel(AuthenticatedUser currentUser, @PathVariable UUID reservationId,
                                      @Valid @RequestBody(required = false) CancelReservationRequest request) {
        return reservationService.cancel(currentUser, reservationId, request == null ? null : request.reason());
    }

    @PostMapping("/reservations/{reservationId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Marcar que la reserva se jugó (sede)")
    public ReservationResponse complete(AuthenticatedUser currentUser, @PathVariable UUID reservationId) {
        return reservationService.markOutcome(currentUser, reservationId, ReservationStatus.COMPLETED);
    }

    @PostMapping("/reservations/{reservationId}/no-show")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Marcar que el cliente no llegó (sede)")
    public ReservationResponse noShow(AuthenticatedUser currentUser, @PathVariable UUID reservationId) {
        return reservationService.markOutcome(currentUser, reservationId, ReservationStatus.NO_SHOW);
    }

    @GetMapping("/venues/{venueId}/reservations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Agenda de reservas de una sede entre dos fechas (inclusive)")
    public PageResponse<ReservationResponse> forVenue(AuthenticatedUser currentUser, @PathVariable UUID venueId,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) ReservationStatus status,
                                                      @RequestParam(required = false) UUID courtId,
                                                      @PageableDefault(size = 50, sort = "startAt") Pageable pageable) {
        Pageable safePageable = Pageables.restrict(pageable, SORTABLE_FIELDS);
        return toPage(reservationService.listForVenue(currentUser, venueId, from, to, status, courtId, safePageable));
    }

    private static PageResponse<ReservationResponse> toPage(Page<ReservationResponse> page) {
        return PageResponse.of(page, r -> r);
    }

}
