package com.sinteticas.api.auth;

import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginThrottleIntegrationTest extends IntegrationTest {

    @Test
    void accountIsThrottledAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        String ip = "10.0.0.1";

        for (int i = 0; i < 5; i++) {
            attempt(email, "incorrecta123", ip).andExpect(status().isUnauthorized());
        }

        // Con la contraseña correcta también se bloquea: si no, el atacante sabría cuándo acertó
        attempt(email, DEFAULT_PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        // Desde otra IP la cuenta sigue protegida
        attempt(email, DEFAULT_PASSWORD, "10.0.0.2").andExpect(status().isTooManyRequests());

        // Pasada la ventana de 15 minutos se puede volver a entrar
        clock.advance(Duration.ofMinutes(16));
        attempt(email, DEFAULT_PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsTheEmailCounter() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        String ip = "10.0.1.1";

        for (int i = 0; i < 4; i++) {
            attempt(email, "incorrecta123", ip).andExpect(status().isUnauthorized());
        }
        attempt(email, DEFAULT_PASSWORD, ip).andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            attempt(email, "incorrecta123", ip).andExpect(status().isUnauthorized());
        }
        attempt(email, DEFAULT_PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void ipIsThrottledWhenTryingManyAccounts() throws Exception {
        String ip = "10.0.2.1";
        for (int i = 0; i < 20; i++) {
            attempt(uniqueEmail(), "incorrecta123", ip).andExpect(status().isUnauthorized());
        }

        String email = uniqueEmail();
        registerCustomer(email);
        attempt(email, DEFAULT_PASSWORD, ip).andExpect(status().isTooManyRequests());
        attempt(email, DEFAULT_PASSWORD, "10.0.2.2").andExpect(status().isOk());
    }

    private ResultActions attempt(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

}
