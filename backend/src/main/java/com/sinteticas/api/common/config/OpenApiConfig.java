package com.sinteticas.api.common.config;

import com.sinteticas.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API. Disponible en http://localhost:8080/swagger-ui.html
 * (botón "Authorize" para pegar el token de /auth/login).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    static {
        // AuthenticatedUser lo inyecta el backend desde el token: no es un parámetro de la petición
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthenticatedUser.class);
    }

    @Bean
    public OpenAPI sinteticasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sintéticas API")
                        .description("API para la reserva de canchas sintéticas en Girardot")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

}
