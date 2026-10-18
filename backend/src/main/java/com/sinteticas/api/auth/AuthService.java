package com.sinteticas.api.auth;

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

    public AuthService(UserService userService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** El registro público siempre crea clientes; los roles ADMIN los asigna un SUPER_ADMIN. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = userService.register(request.fullName(), request.email(), request.password(), RoleName.CUSTOMER);
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Mismo mensaje para "no existe" y "contraseña incorrecta": no revelamos qué emails están registrados
        User user = userRepository.findByEmail(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email o contraseña incorrectos"));

        if (!user.isEnabled()) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "La cuenta está desactivada");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        String token = jwtService.issueToken(user);
        return AuthResponse.bearer(token, jwtService.expiration().toSeconds(), UserResponse.from(user));
    }

}
