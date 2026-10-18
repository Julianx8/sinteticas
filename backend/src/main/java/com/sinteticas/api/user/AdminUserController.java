package com.sinteticas.api.user;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.web.PageResponse;
import com.sinteticas.api.common.web.Pageables;
import com.sinteticas.api.user.dto.UpdateRolesRequest;
import com.sinteticas.api.user.dto.UpdateStatusRequest;
import com.sinteticas.api.user.dto.UserResponse;
import com.sinteticas.api.venue.VenueService;
import com.sinteticas.api.venue.dto.VenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Administración de usuarios", description = "Solo SUPER_ADMIN")
public class AdminUserController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "fullName", "email");

    private static final Set<String> VENUE_SORTABLE_FIELDS = Set.of("name", "createdAt");

    private final UserService userService;
    private final VenueService venueService;

    public AdminUserController(UserService userService, VenueService venueService) {
        this.userService = userService;
        this.venueService = venueService;
    }

    @GetMapping
    @Operation(summary = "Buscar usuarios por nombre o email, opcionalmente filtrando por rol")
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) RoleName role,
                                             @PageableDefault(size = 20, sort = "createdAt",
                                                     direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable safePageable = Pageables.restrict(pageable, SORTABLE_FIELDS);
        return PageResponse.of(userService.search(q, role, safePageable), UserResponse::from);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Ver un usuario")
    public UserResponse get(@PathVariable UUID userId) {
        return UserResponse.from(userService.getById(userId));
    }

    @GetMapping("/{userId}/venues")
    @Operation(summary = "Sedes de las que el usuario es dueño (incluye inactivas)")
    public PageResponse<VenueResponse> venues(@PathVariable UUID userId,
                                              @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        userService.getById(userId);
        Pageable safePageable = Pageables.restrict(pageable, VENUE_SORTABLE_FIELDS);
        return PageResponse.of(venueService.listOwnedBy(userId, safePageable), VenueResponse::from);
    }

    @PutMapping("/{userId}/roles")
    @Operation(summary = "Reemplazar los roles de un usuario (p. ej. convertir a un dueño de cancha en ADMIN)")
    public UserResponse updateRoles(AuthenticatedUser currentUser, @PathVariable UUID userId,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return UserResponse.from(userService.updateRoles(currentUser.id(), userId, request.roles()));
    }

    @PutMapping("/{userId}/status")
    @Operation(summary = "Activar o desactivar una cuenta")
    public UserResponse updateStatus(AuthenticatedUser currentUser, @PathVariable UUID userId,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return UserResponse.from(userService.updateStatus(currentUser.id(), userId, request.enabled()));
    }

}
