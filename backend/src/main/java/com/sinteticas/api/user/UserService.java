package com.sinteticas.api.user;

import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.user.dto.ChangePasswordRequest;
import com.sinteticas.api.user.dto.UpdateProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Usuario no encontrado"));
    }

    @Transactional
    public User register(String fullName, String email, String rawPassword, RoleName roleName) {
        if (userRepository.existsByEmail(email.trim())) {
            throw ApiException.conflict("EMAIL_ALREADY_REGISTERED", "Ya existe una cuenta con ese email");
        }
        User user = new User(fullName, email, passwordEncoder.encode(rawPassword));
        user.addRole(findRole(roleName));
        // flush: aplica el índice único ahora (no al terminar la transacción) y llena created_at
        return userRepository.saveAndFlush(user);
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getById(userId);
        user.setFullName(request.fullName());
        return user;
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "La contraseña actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional(readOnly = true)
    public Page<User> search(String query, RoleName role, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        return userRepository.search(q, role, pageable);
    }

    @Transactional
    public User updateRoles(UUID actorId, UUID userId, Set<RoleName> roleNames) {
        if (actorId.equals(userId) && !roleNames.contains(RoleName.SUPER_ADMIN)) {
            throw ApiException.businessRule("CANNOT_REMOVE_OWN_SUPER_ADMIN",
                    "No puedes quitarte a ti mismo el rol SUPER_ADMIN");
        }
        User user = getById(userId);
        List<Role> roles = roleRepository.findByNameIn(roleNames);
        user.replaceRoles(roles);
        return user;
    }

    @Transactional
    public User updateStatus(UUID actorId, UUID userId, boolean enabled) {
        if (actorId.equals(userId) && !enabled) {
            throw ApiException.businessRule("CANNOT_DISABLE_SELF", "No puedes desactivar tu propia cuenta");
        }
        User user = getById(userId);
        user.setEnabled(enabled);
        return user;
    }

    Role findRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Rol no sembrado en la base de datos: " + roleName));
    }

}
