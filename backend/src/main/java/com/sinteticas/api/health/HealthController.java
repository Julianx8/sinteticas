package com.sinteticas.api.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Estado del servicio")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Comprobar que el backend está funcionando")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "application", "sinteticas-api"
        );
    }

}
