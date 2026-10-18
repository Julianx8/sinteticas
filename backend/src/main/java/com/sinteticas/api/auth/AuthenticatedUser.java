package com.sinteticas.api.auth;

import com.sinteticas.api.user.RoleName;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Usuario que hace la petición, tomado del token JWT.
 * Se puede recibir como parámetro en cualquier controlador (ver {@link AuthenticatedUserArgumentResolver}).
 */
public record AuthenticatedUser(UUID id, Set<RoleName> roles) {

    public static AuthenticatedUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtService.ROLES_CLAIM);
        Set<RoleName> roleNames = roles == null ? Set.of()
                : roles.stream().map(RoleName::valueOf).collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), roleNames);
    }

    /**
     * Para endpoints públicos que muestran más información al dueño: usar con
     * {@code @AuthenticationPrincipal Jwt jwt}, que es {@code null} si no hay token.
     */
    public static Optional<AuthenticatedUser> fromNullable(Jwt jwt) {
        return Optional.ofNullable(jwt).map(AuthenticatedUser::from);
    }

    public boolean isSuperAdmin() {
        return roles.contains(RoleName.SUPER_ADMIN);
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

}
