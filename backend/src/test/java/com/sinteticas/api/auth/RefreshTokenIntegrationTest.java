package com.sinteticas.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIntegrationTest extends IntegrationTest {

    @Test
    void loginReturnsShortAccessTokenAndRefreshToken() throws Exception {
        JsonNode auth = registerCustomer(uniqueEmail());

        assertThat(auth.get("expiresIn").asLong()).isEqualTo(15 * 60);
        assertThat(auth.get("refreshToken").asText()).hasSizeGreaterThan(40);
        assertThat(auth.get("refreshExpiresIn").asLong()).isEqualTo(14 * 24 * 3600);
    }

    @Test
    void accessTokenExpiresAfter15MinutesAndRefreshGivesANewOne() throws Exception {
        JsonNode auth = registerCustomer(uniqueEmail());
        String accessToken = auth.get("accessToken").asText();

        clock.advance(Duration.ofMinutes(14));
        mockMvc.perform(withToken(get("/api/v1/users/me"), accessToken)).andExpect(status().isOk());

        // 15 minutos + 1 de tolerancia por diferencias de reloj
        clock.advance(Duration.ofMinutes(3));
        mockMvc.perform(withToken(get("/api/v1/users/me"), accessToken)).andExpect(status().isUnauthorized());

        String renewed = readJson(refresh(auth.get("refreshToken").asText()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(withToken(get("/api/v1/users/me"), renewed)).andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheWholeSession() throws Exception {
        String first = registerCustomer(uniqueEmail()).get("refreshToken").asText();

        JsonNode renewed = readJson(refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String second = renewed.get("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        assertThat(renewed.get("accessToken").asText()).isNotBlank();

        // Alguien reutiliza el token viejo: se detecta y se cierra la sesión completa...
        refresh(first).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        // ...incluido el token nuevo que tenía el usuario legítimo
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        refresh("token-que-no-existe").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String token = registerCustomer(uniqueEmail()).get("refreshToken").asText();

        clock.advance(Duration.ofDays(15));
        refresh(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        String token = registerCustomer(uniqueEmail()).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", token))))
                .andExpect(status().isNoContent());

        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEverywhereRevokesAllSessions() throws Exception {
        String email = uniqueEmail();
        JsonNode phone = registerCustomer(email);
        String laptop = loginResponse(email, DEFAULT_PASSWORD).get("refreshToken").asText();

        mockMvc.perform(withToken(delete("/api/v1/users/me/sessions"), phone.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        refresh(phone.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        refresh(laptop).andExpect(status().isUnauthorized());
    }

    @Test
    void newRolesApplyOnRefreshWithoutLoggingInAgain() throws Exception {
        JsonNode auth = registerCustomer(uniqueEmail());
        String userId = auth.get("user").get("id").asText();
        String oldAccess = auth.get("accessToken").asText();

        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/roles", userId), superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", new String[]{"CUSTOMER", "ADMIN"}))))
                .andExpect(status().isOk());

        // El token viejo todavía no tiene el rol
        mockMvc.perform(withToken(post("/api/v1/venues"), oldAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody("Sede nueva"))))
                .andExpect(status().isForbidden());

        String newAccess = readJson(refresh(auth.get("refreshToken").asText())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(withToken(post("/api/v1/venues"), newAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody("Sede nueva"))))
                .andExpect(status().isCreated());
    }

    @Test
    void disablingAccountRevokesItsSessions() throws Exception {
        JsonNode auth = registerCustomer(uniqueEmail());

        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/status", auth.get("user").get("id").asText()),
                        superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());

        refresh(auth.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRevokesSessions() throws Exception {
        JsonNode auth = registerCustomer(uniqueEmail());

        mockMvc.perform(withToken(put("/api/v1/users/me/password"), auth.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", DEFAULT_PASSWORD, "newPassword", "OtraClave123"))))
                .andExpect(status().isNoContent());

        refresh(auth.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private JsonNode loginResponse(String email, String password) throws Exception {
        return readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

}
