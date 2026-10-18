package com.sinteticas.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerReturnsTokenAndCustomerRole() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = registerCustomer(email);

        assertThat(auth.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(auth.get("accessToken").asText()).isNotBlank();
        assertThat(auth.get("user").get("email").asText()).isEqualTo(email);
        assertThat(auth.get("user").get("roles").toString()).isEqualTo("[\"CUSTOMER\"]");
        assertThat(auth.get("user").has("createdAt")).isTrue();
    }

    @Test
    void emailIsCaseInsensitiveAndNormalized() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Otro", "email", email.toUpperCase(), "password", DEFAULT_PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        // Se puede iniciar sesión escribiendo el email en mayúsculas
        login("  " + email.toUpperCase() + " ", DEFAULT_PASSWORD);
    }

    @Test
    void registerValidatesFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "", "email", "no-es-email", "password", "corta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "incorrecta123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(withToken(get("/api/v1/users/me"), "token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUserAndProfileCanBeUpdated() throws Exception {
        String email = uniqueEmail();
        String token = registerCustomer(email).get("accessToken").asText();

        mockMvc.perform(withToken(get("/api/v1/users/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(withToken(patch("/api/v1/users/me"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Nombre Nuevo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nombre Nuevo"));
    }

    @Test
    void changePasswordRequiresCurrentPassword() throws Exception {
        String email = uniqueEmail();
        String token = registerCustomer(email).get("accessToken").asText();

        mockMvc.perform(withToken(put("/api/v1/users/me/password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "equivocada", "newPassword", "NuevaClave123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

        mockMvc.perform(withToken(put("/api/v1/users/me/password"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", DEFAULT_PASSWORD, "newPassword", "NuevaClave123"))))
                .andExpect(status().isNoContent());

        login(email, "NuevaClave123");
    }

}
