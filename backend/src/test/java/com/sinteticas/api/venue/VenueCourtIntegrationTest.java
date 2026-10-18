package com.sinteticas.api.venue;

import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VenueCourtIntegrationTest extends IntegrationTest {

    @Test
    void customerCannotCreateVenue() throws Exception {
        mockMvc.perform(withToken(post("/api/v1/venues"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody("Sede X"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotCreateVenue() throws Exception {
        mockMvc.perform(post("/api/v1/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody("Sede X"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCreatesVenueAndCourtsVisibleInPublicCatalog() throws Exception {
        String admin = adminToken();
        String uniqueName = "Sede " + UUID.randomUUID();
        String venueId = createVenue(admin, uniqueName);
        createCourt(admin, venueId, "Cancha 1", "FUTBOL_5");

        // El catálogo público no necesita token
        mockMvc.perform(get("/api/v1/venues").param("q", uniqueName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value(uniqueName));

        mockMvc.perform(get("/api/v1/venues").param("q", uniqueName).param("courtType", "FUTBOL_11"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/v1/venues/{id}/courts", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cancha 1"))
                .andExpect(jsonPath("$[0].venueName").value(uniqueName));

        mockMvc.perform(withToken(get("/api/v1/venues/mine"), admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyOwnerCanModifyVenueAndCourts() throws Exception {
        String owner = adminToken();
        String otherAdmin = adminToken();
        String venueId = createVenue(owner, "Sede propia");
        String courtId = createCourt(owner, venueId, "Cancha A", "FUTBOL_7");

        mockMvc.perform(withToken(put("/api/v1/venues/{id}", venueId), otherAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody("Robada"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENUE_NOT_OWNED"));

        mockMvc.perform(withToken(put("/api/v1/courts/{id}", courtId), otherAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "X", "courtType", "FUTBOL_5"))))
                .andExpect(status().isForbidden());

        // El SUPER_ADMIN sí puede
        mockMvc.perform(withToken(put("/api/v1/venues/{id}", venueId), superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody("Renombrada por soporte"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renombrada por soporte"));
    }

    @Test
    void inactiveVenueIsHiddenFromPublicButVisibleToOwner() throws Exception {
        String owner = adminToken();
        String venueId = createVenue(owner, "Sede cerrada");

        Map<String, Object> body = venueBody("Sede cerrada");
        body.put("active", false);
        mockMvc.perform(withToken(put("/api/v1/venues/{id}", venueId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/venues/{id}", venueId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VENUE_NOT_FOUND"));

        mockMvc.perform(withToken(get("/api/v1/venues/{id}", venueId), owner))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveCourtIsHiddenFromPublicList() throws Exception {
        String owner = adminToken();
        String venueId = createVenue(owner, "Sede con cancha inactiva");
        createCourt(owner, venueId, "Activa", "FUTBOL_5");
        String inactiveId = createCourt(owner, venueId, "Inactiva", "FUTBOL_5");

        mockMvc.perform(withToken(put("/api/v1/courts/{id}", inactiveId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Inactiva", "courtType", "FUTBOL_5", "active", false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/venues/{id}/courts", venueId))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(withToken(get("/api/v1/venues/{id}/courts", venueId), owner))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/courts/{id}", inactiveId))
                .andExpect(status().isNotFound());
    }

    @Test
    void courtNamesAreUniquePerVenueIgnoringCase() throws Exception {
        String owner = adminToken();
        String venueId = createVenue(owner, "Sede nombres");
        createCourt(owner, venueId, "Cancha 1", "FUTBOL_5");

        mockMvc.perform(withToken(post("/api/v1/venues/{id}/courts", venueId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "CANCHA 1", "courtType", "FUTBOL_7"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURT_NAME_TAKEN"));
    }

    @Test
    void invalidCourtTypeIsRejected() throws Exception {
        String owner = adminToken();
        String venueId = createVenue(owner, "Sede tipos");

        mockMvc.perform(withToken(post("/api/v1/venues/{id}/courts", venueId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Cancha", "courtType", "BALONCESTO"))))
                .andExpect(status().isBadRequest());
    }

    private static Map<String, Object> venueBody(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("address", "Calle 10 # 5-20");
        body.put("neighborhood", "Centro");
        body.put("phone", "+57 300 123 4567");
        return body;
    }

    private String createVenue(String token, String name) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/venues"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(venueBody(name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

    private String createCourt(String token, String venueId, String name, String type) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/venues/{id}/courts", venueId), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "courtType", type))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

}
