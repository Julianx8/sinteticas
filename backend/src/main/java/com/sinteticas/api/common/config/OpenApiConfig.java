package com.sinteticas.api.common.config;

import com.sinteticas.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Orden de las secciones en Swagger: el del recorrido típico (por defecto saldrían en cualquier orden). */
    private static final List<String> TAG_ORDER = List.of(
            "Estado del servicio", "Autenticación", "Mi cuenta", "Administración de usuarios", "Sedes", "Canchas",
            "Horarios y bloqueos", "Reservas", "Reportes", "Festivos");

    @Bean
    public OpenApiCustomizer sortTags() {
        return openApi -> {
            // springdoc solo lista las secciones con descripción; aquí se listan todas, en orden
            Map<String, Tag> existing = new LinkedHashMap<>();
            if (openApi.getTags() != null) {
                openApi.getTags().forEach(tag -> existing.put(tag.getName(), tag));
            }
            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                    if (op.getTags() != null) {
                        op.getTags().forEach(name -> existing.putIfAbsent(name, new Tag().name(name)));
                    }
                }));
            }
            List<Tag> sorted = new ArrayList<>(existing.values());
            sorted.sort(Comparator.comparingInt(tag -> {
                int index = TAG_ORDER.indexOf(tag.getName());
                return index < 0 ? TAG_ORDER.size() : index;
            }));
            openApi.setTags(sorted);
        };
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
