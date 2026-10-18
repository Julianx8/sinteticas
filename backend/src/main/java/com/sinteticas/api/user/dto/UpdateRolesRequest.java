package com.sinteticas.api.user.dto;

import com.sinteticas.api.user.RoleName;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(
        @NotEmpty Set<RoleName> roles
) {
}
