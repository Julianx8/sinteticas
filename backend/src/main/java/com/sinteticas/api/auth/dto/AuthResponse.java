package com.sinteticas.api.auth.dto;

import com.sinteticas.api.user.dto.UserResponse;

/**
 * {@code accessToken} va en el header Authorization (dura {@code expiresIn} segundos);
 * {@code refreshToken} se guarda y se usa en /auth/refresh para obtener uno nuevo (dura {@code refreshExpiresIn}).
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        UserResponse user
) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, String refreshToken,
                                      long refreshExpiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, refreshToken, refreshExpiresInSeconds, user);
    }

}
