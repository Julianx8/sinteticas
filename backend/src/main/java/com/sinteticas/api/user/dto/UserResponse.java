package com.sinteticas.api.user.dto;

import com.sinteticas.api.user.RoleName;
import com.sinteticas.api.user.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        List<RoleName> roles,
        boolean enabled,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRoleNames().stream().sorted().toList(),
                user.isEnabled(),
                user.getCreatedAt());
    }

}
