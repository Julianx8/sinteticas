package com.sinteticas.api.auth;

import com.sinteticas.api.user.RoleName;
import com.sinteticas.api.user.RoleRepository;
import com.sinteticas.api.user.User;
import com.sinteticas.api.user.UserRepository;
import com.sinteticas.api.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer SUPER_ADMIN al arrancar, a partir de SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD.
 * Sin él nadie podría asignar el rol ADMIN a los dueños de cancha.
 * Si el usuario ya existe, solo se asegura de que tenga el rol (no cambia su contraseña).
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final SecurityProperties.SuperAdmin config;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;

    public SuperAdminBootstrap(SecurityProperties properties, UserRepository userRepository,
                               RoleRepository roleRepository, UserService userService) {
        this.config = properties.bootstrap().superAdmin();
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!config.isConfigured()) {
            log.info("SUPER_ADMIN_EMAIL/SUPER_ADMIN_PASSWORD no definidos: no se crea super administrador");
            return;
        }

        userRepository.findByEmail(config.email().trim()).ifPresentOrElse(user -> {
            if (!user.hasRole(RoleName.SUPER_ADMIN)) {
                user.addRole(roleRepository.findByName(RoleName.SUPER_ADMIN).orElseThrow());
                log.info("Usuario {} promovido a SUPER_ADMIN", user.getEmail());
            }
        }, () -> {
            User created = userService.register(config.fullName(), config.email(), config.password(),
                    RoleName.SUPER_ADMIN);
            log.info("Super administrador creado: {}", created.getEmail());
        });
    }

}
