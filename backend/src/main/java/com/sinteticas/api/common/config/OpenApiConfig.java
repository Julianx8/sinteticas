package com.sinteticas.api.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API. Disponible en http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sinteticasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sintéticas API")
                        .description("API para la reserva de canchas sintéticas en Girardot")
                        .version("v1"));
    }

}
