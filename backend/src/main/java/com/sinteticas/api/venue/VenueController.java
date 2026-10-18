package com.sinteticas.api.venue;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.web.PageResponse;
import com.sinteticas.api.common.web.Pageables;
import com.sinteticas.api.court.CourtType;
import com.sinteticas.api.venue.dto.VenueRequest;
import com.sinteticas.api.venue.dto.VenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Sedes")
public class VenueController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "neighborhood", "createdAt");

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping
    @Operation(summary = "Catálogo público de sedes activas",
            description = "Filtros opcionales: texto (nombre o dirección), barrio y tipo de cancha disponible")
    public PageResponse<VenueResponse> search(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String neighborhood,
                                              @RequestParam(required = false) CourtType courtType,
                                              @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Pageable safePageable = Pageables.restrict(pageable, SORTABLE_FIELDS);
        return PageResponse.of(venueService.searchActive(q, neighborhood, courtType, safePageable),
                VenueResponse::from);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Sedes que administro (incluye inactivas)")
    public PageResponse<VenueResponse> mine(AuthenticatedUser currentUser,
                                            @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Pageable safePageable = Pageables.restrict(pageable, SORTABLE_FIELDS);
        return PageResponse.of(venueService.listOwnedBy(currentUser.id(), safePageable), VenueResponse::from);
    }

    @GetMapping("/{venueId}")
    @Operation(summary = "Detalle de una sede")
    public VenueResponse get(@PathVariable UUID venueId, @AuthenticationPrincipal Jwt jwt) {
        return VenueResponse.from(venueService.getVisible(venueId, AuthenticatedUser.fromNullable(jwt)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Crear una sede (el usuario autenticado queda como dueño)")
    public VenueResponse create(AuthenticatedUser currentUser, @Valid @RequestBody VenueRequest request) {
        return VenueResponse.from(venueService.create(currentUser, request));
    }

    @PutMapping("/{venueId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Actualizar una sede (dueño o SUPER_ADMIN). Enviar active=false para desactivarla")
    public VenueResponse update(AuthenticatedUser currentUser, @PathVariable UUID venueId,
                                @Valid @RequestBody VenueRequest request) {
        return VenueResponse.from(venueService.update(currentUser, venueId, request));
    }

}
