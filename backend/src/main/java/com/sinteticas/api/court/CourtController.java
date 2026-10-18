package com.sinteticas.api.court;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.court.dto.CourtRequest;
import com.sinteticas.api.court.dto.CourtResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Canchas")
public class CourtController {

    private final CourtService courtService;

    public CourtController(CourtService courtService) {
        this.courtService = courtService;
    }

    @GetMapping("/venues/{venueId}/courts")
    @Operation(summary = "Canchas de una sede (el dueño ve también las inactivas)")
    public List<CourtResponse> listForVenue(@PathVariable UUID venueId, @AuthenticationPrincipal Jwt jwt) {
        return courtService.listForVenue(venueId, AuthenticatedUser.fromNullable(jwt)).stream()
                .map(CourtResponse::from)
                .toList();
    }

    @GetMapping("/courts/{courtId}")
    @Operation(summary = "Detalle de una cancha")
    public CourtResponse get(@PathVariable UUID courtId, @AuthenticationPrincipal Jwt jwt) {
        return CourtResponse.from(courtService.getVisible(courtId, AuthenticatedUser.fromNullable(jwt)));
    }

    @PostMapping("/venues/{venueId}/courts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Crear una cancha en una sede propia")
    public CourtResponse create(AuthenticatedUser currentUser, @PathVariable UUID venueId,
                                @Valid @RequestBody CourtRequest request) {
        return CourtResponse.from(courtService.create(currentUser, venueId, request));
    }

    @PutMapping("/courts/{courtId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Actualizar una cancha (active=false para desactivarla)")
    public CourtResponse update(AuthenticatedUser currentUser, @PathVariable UUID courtId,
                                @Valid @RequestBody CourtRequest request) {
        return CourtResponse.from(courtService.update(currentUser, courtId, request));
    }

}
