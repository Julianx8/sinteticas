package com.sinteticas.api.auth;

import com.sinteticas.api.auth.RefreshTokenService.ClientInfo;
import com.sinteticas.api.auth.dto.AuthResponse;
import com.sinteticas.api.auth.dto.LoginRequest;
import com.sinteticas.api.auth.dto.RefreshRequest;
import com.sinteticas.api.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un cliente nuevo y abrir su sesión")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, clientInfo(httpRequest));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión con email y contraseña",
            description = "Tras 5 intentos fallidos en 15 minutos responde 429 con el header Retry-After")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientInfo(httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el token de acceso con el refresh token",
            description = "Devuelve un par nuevo; el refresh token usado deja de servir. "
                    + "Reutilizar uno ya usado cierra la sesión (REFRESH_TOKEN_REUSED)")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), clientInfo(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar la sesión de este refresh token")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    private static ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }

}
