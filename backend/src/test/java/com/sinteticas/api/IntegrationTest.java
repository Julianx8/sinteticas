package com.sinteticas.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base para tests de integración: aplicación completa + PostgreSQL real (Testcontainers).
 * Todas las clases que la extienden comparten el mismo contexto y el mismo contenedor.
 * Cada test crea sus propios usuarios con emails únicos, así no dependen entre sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {

    protected static final String SUPER_ADMIN_EMAIL = "superadmin@test.com";
    protected static final String SUPER_ADMIN_PASSWORD = "SuperAdmin123";
    protected static final String DEFAULT_PASSWORD = "Password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.com";
    }

    protected static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    /** Registra un cliente y devuelve la respuesta de autenticación (token + usuario). */
    protected JsonNode registerCustomer(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Cliente Test", "email", email, "password", DEFAULT_PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body);
    }

    protected String customerToken() throws Exception {
        return registerCustomer(uniqueEmail()).get("accessToken").asText();
    }

    protected String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("accessToken").asText();
    }

    protected String superAdminToken() throws Exception {
        return login(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD);
    }

    /** Crea un usuario con rol ADMIN (dueño de cancha) y devuelve su token ya con el rol. */
    protected String adminToken() throws Exception {
        String email = uniqueEmail();
        String userId = registerCustomer(email).get("user").get("id").asText();
        mockMvc.perform(withToken(put("/api/v1/admin/users/{id}/roles", userId), superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("roles", new String[]{"CUSTOMER", "ADMIN"}))))
                .andExpect(status().isOk());
        return login(email, DEFAULT_PASSWORD);
    }

    protected static Map<String, Object> venueBody(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("address", "Calle 10 # 5-20");
        body.put("neighborhood", "Centro");
        body.put("phone", "+57 300 123 4567");
        return body;
    }

    protected String createVenue(String token, String name) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/venues"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

    protected String createCourt(String token, String venueId, String name, String type) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/venues/{id}/courts", venueId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "courtType", type))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

}
