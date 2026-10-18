package com.sinteticas.api.schedule;

import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleIntegrationTest extends IntegrationTest {

    @Test
    void ownerDefinesWeeklyScheduleWithDifferentPrices() throws Exception {
        String owner = adminToken();
        String courtId = createCourt(owner, createVenue(owner, "Sede horarios"), "Cancha 1", "FUTBOL_5");

        List<Map<String, Object>> slots = List.of(
                slot(1, 18, 23, 90000),
                slot(1, 8, 18, 60000),
                slot(6, 8, 24, 100000));

        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", slots))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Ordenado por día y hora
                .andExpect(jsonPath("$[0].startHour").value(8))
                .andExpect(jsonPath("$[1].startHour").value(18))
                .andExpect(jsonPath("$[2].endHour").value(24));

        // Consulta pública
        mockMvc.perform(get("/api/v1/courts/{id}/schedules", courtId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].pricePerHour").value(90000));

        // Reemplazar por un horario nuevo borra el anterior
        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", List.of(slot(2, 10, 12, 50000))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void overlappingSlotsAreRejected() throws Exception {
        String owner = adminToken();
        String courtId = createCourt(owner, createVenue(owner, "Sede solape"), "Cancha 1", "FUTBOL_5");

        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", List.of(slot(3, 8, 18, 60000), slot(3, 17, 22, 80000))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        String owner = adminToken();
        String courtId = createCourt(owner, createVenue(owner, "Sede rangos"), "Cancha 1", "FUTBOL_5");

        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", List.of(slot(3, 18, 8, 60000))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHEDULE_RANGE"));

        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", List.of(slot(8, 8, 10, 60000))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void otherAdminCannotChangeSchedule() throws Exception {
        String owner = adminToken();
        String courtId = createCourt(owner, createVenue(owner, "Sede ajena"), "Cancha 1", "FUTBOL_5");

        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", List.of(slot(1, 8, 10, 1000))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCreatesListsAndDeletesBlocks() throws Exception {
        String owner = adminToken();
        String courtId = createCourt(owner, createVenue(owner, "Sede bloqueos"), "Cancha 1", "FUTBOL_5");

        String body = mockMvc.perform(withToken(post("/api/v1/courts/{id}/blocks", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startAt", "2030-12-24T00:00:00-05:00",
                                "endAt", "2030-12-26T00:00:00-05:00",
                                "reason", "Navidad"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value("2030-12-24T00:00:00-05:00"))
                .andReturn().getResponse().getContentAsString();
        String blockId = readJson(body).get("id").asText();

        mockMvc.perform(withToken(get("/api/v1/courts/{id}/blocks", courtId), owner)
                        .param("from", "2030-12-01").param("to", "2030-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reason").value("Navidad"));

        mockMvc.perform(withToken(delete("/api/v1/courts/{id}/blocks/{blockId}", courtId, blockId), owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(withToken(get("/api/v1/courts/{id}/blocks", courtId), owner)
                        .param("from", "2030-12-01").param("to", "2030-12-31"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blockWithEndBeforeStartIsRejected() throws Exception {
        String owner = adminToken();
        String courtId = createCourt(owner, createVenue(owner, "Sede bloqueo inválido"), "Cancha 1", "FUTBOL_5");

        mockMvc.perform(withToken(post("/api/v1/courts/{id}/blocks", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startAt", "2030-12-26T00:00:00-05:00",
                                "endAt", "2030-12-24T00:00:00-05:00",
                                "reason", "Al revés"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    private static Map<String, Object> slot(int day, int start, int end, int price) {
        return Map.of("dayOfWeek", day, "startHour", start, "endHour", end, "pricePerHour", price);
    }

}
