package com.sinteticas.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.ZoneId;

/**
 * Infraestructura de tests: reloj controlable y PostgreSQL real (misma versión que docker-compose).
 * Spring lo conecta solo gracias a {@link ServiceConnection}; Flyway aplica las migraciones al arrancar.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /** Reemplaza el reloj del sistema para poder mover la hora en los tests. */
    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(ZoneId.of("America/Bogota"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16");
    }

}
