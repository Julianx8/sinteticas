package com.sinteticas.api.venue;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase B: resumen de canchas en el catálogo, búsqueda por fecha y hora, y disponibilidad de toda la sede.
 * Cada test usa un prefijo único en el nombre de sus sedes para no ver las de otros tests.
 */
class VenueCatalogIntegrationTest extends IntegrationTest {

    private String owner;
    private String prefix;
    private LocalDate date;

    @BeforeEach
    void setUp() throws Exception {
        owner = adminToken();
        prefix = "Cat" + UUID.randomUUID().toString().substring(0, 8);
        date = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(5);
    }

    @Test
    void catalogShowsCourtTypesAndLowestPricePerHour() throws Exception {
        String venueId = createVenue(owner, prefix + " Sede");
        String f5 = createCourt(owner, venueId, "Cancha 1", "FUTBOL_5");
        String f7 = createCourt(owner, venueId, "Cancha 2", "FUTBOL_7");
        schedule(f5, 8, 18, 70000, 18, 23, 90000);
        schedule(f7, 8, 23, 110000);
        createCourt(owner, venueId, "Cancha vieja", "FUTBOL_11"); // sin horario: igual cuenta su tipo
        String emptyVenue = createVenue(owner, prefix + " Vacía");

        JsonNode page = search("?q=" + prefix + "&sort=name");
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        JsonNode first = page.get("content").get(0);
        assertThat(first.get("id").asText()).isEqualTo(venueId);
        assertThat(first.get("courtTypes")).extracting(JsonNode::asText)
                .containsExactly("FUTBOL_5", "FUTBOL_7", "FUTBOL_11");
        assertThat(first.get("minPricePerHour").decimalValue()).isEqualByComparingTo("70000");

        JsonNode empty = page.get("content").get(1);
        assertThat(empty.get("id").asText()).isEqualTo(emptyVenue);
        assertThat(empty.get("courtTypes")).isEmpty();
        assertThat(empty.hasNonNull("minPricePerHour")).as("sin horarios no hay precio (el campo se omite)").isFalse();

        // El detalle trae el mismo resumen
        mockMvc.perform(get("/api/v1/venues/{id}", venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minPricePerHour").value(70000));
    }

    @Test
    void searchByDateAndHourOnlyReturnsVenuesWithAFreeCourt() throws Exception {
        String free = createVenue(owner, prefix + " Libre");
        schedule(createCourt(owner, free, "Cancha 1", "FUTBOL_5"), 8, 23, 80000);

        String booked = createVenue(owner, prefix + " Ocupada");
        String bookedCourt = createCourt(owner, booked, "Cancha 1", "FUTBOL_5");
        schedule(bookedCourt, 8, 23, 80000);
        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", bookedCourt, "startAt", date + "T19:00:00-05:00", "durationHours", 2))))
                .andExpect(status().isCreated());

        String blocked = createVenue(owner, prefix + " Bloqueada");
        String blockedCourt = createCourt(owner, blocked, "Cancha 1", "FUTBOL_5");
        schedule(blockedCourt, 8, 23, 80000);
        mockMvc.perform(withToken(post("/api/v1/courts/{id}/blocks", blockedCourt), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("startAt", date + "T18:00:00-05:00", "endAt", date + "T22:00:00-05:00",
                                "reason", "Torneo"))))
                .andExpect(status().isCreated());

        String closed = createVenue(owner, prefix + " Cerrada");
        schedule(createCourt(owner, closed, "Cancha 1", "FUTBOL_5"), 8, 18, 80000); // cierra a las 18

        String otherType = createVenue(owner, prefix + " Once");
        schedule(createCourt(owner, otherType, "Cancha 1", "FUTBOL_11"), 8, 23, 200000);

        assertThat(names(search("?q=" + prefix + "&date=" + date + "&hour=20")))
                .containsExactlyInAnyOrder(prefix + " Libre", prefix + " Once");
        assertThat(names(search("?q=" + prefix + "&date=" + date + "&hour=20&courtType=FUTBOL_5")))
                .containsExactly(prefix + " Libre");
        // A las 10 de la mañana todas abren y nada está ocupado
        assertThat(names(search("?q=" + prefix + "&date=" + date + "&hour=10"))).hasSize(5);
    }

    @Test
    void searchByAvailabilityValidatesItsParameters() throws Exception {
        mockMvc.perform(get("/api/v1/venues?date=" + date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH"));
        mockMvc.perform(get("/api/v1/venues?date=" + date + "&hour=24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HOUR"));
        mockMvc.perform(get("/api/v1/venues?date=" + LocalDate.now().minusDays(1) + "&hour=10"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESERVATION_IN_PAST"));
        mockMvc.perform(get("/api/v1/venues?date=" + LocalDate.now().plusDays(60) + "&hour=10"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_FAR_AHEAD"));
    }

    @Test
    void venueAvailabilityReturnsEveryActiveCourtInOneRequest() throws Exception {
        String venueId = createVenue(owner, prefix + " Sede");
        String f5 = createCourt(owner, venueId, "Cancha 1", "FUTBOL_5");
        String f7 = createCourt(owner, venueId, "Cancha 2", "FUTBOL_7");
        schedule(f5, 18, 22, 90000);
        schedule(f7, 8, 12, 100000);
        String inactive = createCourt(owner, venueId, "Cancha 3", "FUTBOL_5");
        mockMvc.perform(withToken(put("/api/v1/courts/{id}", inactive), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Cancha 3", "courtType", "FUTBOL_5", "active", false))))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", f5, "startAt", date + "T19:00:00-05:00", "durationHours", 1))))
                .andExpect(status().isCreated());

        // Público: sin token
        JsonNode body = readJson(mockMvc.perform(get("/api/v1/venues/{id}/availability?date={date}", venueId, date))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        JsonNode courts = body.get("courts");
        assertThat(courts).hasSize(2);
        assertThat(courts.get(0).get("name").asText()).isEqualTo("Cancha 1");
        assertThat(courts.get(0).get("slots")).hasSize(4);
        assertThat(courts.get(0).get("availableHours").asInt()).isEqualTo(3); // 18, 20 y 21 (19 reservada)
        assertThat(courts.get(1).get("courtType").asText()).isEqualTo("FUTBOL_7");
        assertThat(courts.get(1).get("availableHours").asInt()).isEqualTo(4);
    }

    private void schedule(String courtId, int... ranges) throws Exception {
        List<Map<String, Object>> slots = new java.util.ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            for (int i = 0; i < ranges.length; i += 3) {
                slots.add(Map.of("dayOfWeek", day, "startHour", ranges[i], "endHour", ranges[i + 1], "pricePerHour", ranges[i + 2]));
            }
        }
        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", slots))))
                .andExpect(status().isOk());
    }

    private JsonNode search(String query) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/venues" + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new java.util.ArrayList<>();
        page.get("content").forEach(venue -> names.add(venue.get("name").asText()));
        return names;
    }

}
