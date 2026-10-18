package com.sinteticas.api.auth;

import com.sinteticas.api.auth.RefreshTokenService.ClientInfo;
import com.sinteticas.api.auth.dto.AuthResponse;
import com.sinteticas.api.auth.dto.LoginRequest;
import com.sinteticas.api.auth.dto.RegisterRequest;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.user.RoleName;
import com.sinteticas.api.user.User;
import com.sinteticas.api.user.UserRepository;
import com.sinteticas.api.user.UserService;
import com.sinteticas.api.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserService userService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService loginAttempts, RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
        this.refreshTokenService = refreshTokenService;
    }

    /** El registro público siempre crea clientes; los roles ADMIN los asigna un SUPER_ADMIN. */
    @Transactional
    public AuthResponse register(RegisterRequest request, ClientInfo client) {
        User user = userService.register(request.fullName(), request.email(), request.password(), RoleName.CUSTOMER);
        return newSession(user, client);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        String clientIp = client.ip();
        loginAttempts.checkAllowed(request.email(), clientIp);

        // Mismo mensaje para "no existe" y "contraseña incorrecta": no revelamos qué emails están registrados
        User user = userRepository.findByEmail(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginAttempts.recordFailure(request.email(), clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email o contraseña incorrectos");
        }
        loginAttempts.recordSuccess(request.email());

        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está desactivada");
        }
        return newSession(user, client);
    }

    /**
     * Cambia el refresh token por un par nuevo. El token de acceso nuevo lleva los roles ACTUALES
     * del usuario: un cambio de roles se refleja en la siguiente renovación.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String refreshToken, ClientInfo client) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken, client);
        return toResponse(rotation.user(), rotation.token());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse newSession(User user, ClientInfo client) {
        return toResponse(user, refreshTokenService.issue(user, client));
    }

    private AuthResponse toResponse(User user, RefreshTokenService.Issued refresh) {
        String accessToken = jwtService.issueToken(user);
        return AuthResponse.bearer(accessToken, jwtService.expiration().toSeconds(), refresh.value(),
                refreshTokenService.expiration().toSeconds(), UserResponse.from(user));
    }

}
