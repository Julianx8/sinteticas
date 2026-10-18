package com.sinteticas.api.user;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.user.dto.ChangePasswordRequest;
import com.sinteticas.api.user.dto.UpdateProfileRequest;
import com.sinteticas.api.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Mi cuenta")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Datos del usuario autenticado")
    public UserResponse me(AuthenticatedUser currentUser) {
        return UserResponse.from(userService.getById(currentUser.id()));
    }

    @PatchMapping
    @Operation(summary = "Actualizar mi perfil")
    public UserResponse updateProfile(AuthenticatedUser currentUser, @Valid @RequestBody UpdateProfileRequest request) {
        return UserResponse.from(userService.updateProfile(currentUser.id(), request));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar mi contraseña")
    public void changePassword(AuthenticatedUser currentUser, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.id(), request);
    }

}
