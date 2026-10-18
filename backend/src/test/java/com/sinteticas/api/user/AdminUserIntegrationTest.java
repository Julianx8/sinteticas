package com.sinteticas.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserIntegrationTest extends IntegrationTest {

    @Test
    void customerCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/admin/users"), customerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void superAdminCanSearchUsers() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        mockMvc.perform(withToken(get("/api/v1/admin/users").param("q", email), superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(email));
    }

    @Test
    void superAdminFiltersUsersByRoleAndSeesTheirVenues() throws Exception {
        String admin = adminToken();
        String adminId = readJson(mockMvc.perform(withToken(get("/api/v1/users/me"), admin))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String adminEmail = readJson(mockMvc.perform(withToken(get("/api/v1/users/me"), admin))
                .andReturn().getResponse().getContentAsString()).get("email").asText();
        String customerEmail = uniqueEmail();
        registerCustomer(customerEmail);
        createVenue(admin, "Sede del admin");

        mockMvc.perform(withToken(get("/api/v1/admin/users").param("q", adminEmail).param("role", "ADMIN"),
                        superAdminToken()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(withToken(get("/api/v1/admin/users").param("q", customerEmail).param("role", "ADMIN"),
                        superAdminToken()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(withToken(get("/api/v1/admin/users/{id}/venues", adminId), superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Sede del admin"));
    }

    @Test
    void invalidSortReturnsBadRequest() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/admin/users").param("sort", "noExiste"), superAdminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void superAdminCanGrantAdminRole() throws Exception {
        String email = uniqueEmail();
        String userId = registerCustomer(email).get("user").get("id").asText();

        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/roles", userId), superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", new String[]{"CUSTOMER", "ADMIN"}))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.roles[1]").value("ADMIN"));
    }

    @Test
    void superAdminCannotRemoveOwnRoleOrDisableSelf() throws Exception {
        String token = superAdminToken();
        String myId = readJson(mockMvc.perform(withToken(get("/api/v1/users/me"), token))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/roles", myId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", new String[]{"CUSTOMER"}))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWN_SUPER_ADMIN"));

        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/status", myId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = registerCustomer(email);

        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/status", auth.get("user").get("id").asText()),
                        superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

}
