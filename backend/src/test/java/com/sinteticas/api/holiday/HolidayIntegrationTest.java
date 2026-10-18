package com.sinteticas.api.holiday;

import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El lunes 2 de noviembre de 2026 es festivo (Todos los Santos): se cobra con el horario del domingo.
 * El reloj se fija en el 20 de octubre de 2026 para que la fecha quede dentro de la ventana de reservas.
 */
class HolidayIntegrationTest extends IntegrationTest {

    private String courtId;

    @BeforeEach
    void setUp() throws Exception {
        clock.setInstant(OffsetDateTime.parse("2026-10-20T08:00:00-05:00").toInstant());
        String owner = adminToken();
        courtId = createCourt(owner, createVenue(owner, "Sede festivos"), "Cancha 1", "FUTBOL_5");

        // Lunes 08-22 a $60.000; domingo 10-20 a $100.000
        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", List.of(
                                Map.of("dayOfWeek", 1, "startHour", 8, "endHour", 22, "pricePerHour", 60000),
                                Map.of("dayOfWeek", 7, "startHour", 10, "endHour", 20, "pricePerHour", 100000))))))
                .andExpect(status().isOk());
    }

    @Test
    void holidayUsesSundayScheduleAndPrices() throws Exception {
        mockMvc.perform(get("/api/v1/courts/{id}/availability", courtId).param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidayName").value("Día de Todos los Santos"))
                .andExpect(jsonPath("$.slots.length()").value(10))
                .andExpect(jsonPath("$.slots[0].hour").value(10))
                .andExpect(jsonPath("$.slots[0].price").value(100000));

        // Un lunes normal sigue con su horario
        mockMvc.perform(get("/api/v1/courts/{id}/availability", courtId).param("date", "2026-10-26"))
                .andExpect(jsonPath("$.holidayName").doesNotExist())
                .andExpect(jsonPath("$.slots.length()").value(14))
                .andExpect(jsonPath("$.slots[0].price").value(60000));
    }

    @Test
    void reservationOnHolidayIsChargedAtSundayPrice() throws Exception {
        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", courtId, "startAt", "2026-11-02T18:00:00-05:00",
                                "durationHours", 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(200000));

        // A las 21:00 abre un lunes normal, pero el festivo cierra a las 20:00
        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", courtId, "startAt", "2026-11-02T21:00:00-05:00",
                                "durationHours", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUTSIDE_SCHEDULE"));
    }

    @Test
    void holidaysEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(18))
                .andExpect(jsonPath("$[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$[0].name").value("Año Nuevo"));

        mockMvc.perform(get("/api/v1/holidays").param("year", "1500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_YEAR"));
    }

}
