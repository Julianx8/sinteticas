package com.sinteticas.api;

import com.sinteticas.api.notification.MailConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.ZoneId;
import java.util.concurrent.Executor;

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

    /** Correos capturados en memoria en vez de enviarse. */
    @Bean
    CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }

    /** Envío síncrono en los tests: el correo ya está capturado cuando termina la petición. */
    @Bean
    @Primary
    @Qualifier(MailConfig.MAIL_EXECUTOR)
    Executor syncMailExecutor() {
        return new SyncTaskExecutor();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16");
    }

}
