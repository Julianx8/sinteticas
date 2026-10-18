package com.sinteticas.api.report;

import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escenario con números conocidos, para verificar cada fórmula de los reportes.
 * <p>
 * Ambas canchas abren todos los días 08-18 a $60.000 y 18-22 a $90.000 → 14 horas por día.
 * El día D (dentro de una semana):
 * <ul>
 *   <li>Cancha A: 18-20 (2 h, $180.000) → COMPLETED · 08-09 (1 h, $60.000) → NO_SHOW</li>
 *   <li>Cancha B: 19-20 (1 h, $90.000) → CONFIRMED · 10-11 → CANCELLED · bloqueo 12-14</li>
 * </ul>
 * Horas disponibles en D: A 14 + B 12 = 26 · reservadas: 2 + 1 + 1 = 4 → ocupación 4/26 = 0.1538.
 */
class ReportIntegrationTest extends IntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private String owner;
    private String venueId;
    private LocalDate date;

    @BeforeEach
    void setUpScenario() throws Exception {
        owner = adminToken();
        venueId = createVenue(owner, "Sede reportes");
        String courtA = createCourt(owner, venueId, "Cancha A", "FUTBOL_5");
        String courtB = createCourt(owner, venueId, "Cancha B", "FUTBOL_7");
        date = LocalDate.now(BOGOTA).plusDays(7);
        setSchedule(courtA);
        setSchedule(courtB);

        mockMvc.perform(withToken(post("/api/v1/courts/{id}/blocks", courtB), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("startAt", at(12), "endAt", at(14), "reason", "Mantenimiento"))))
                .andExpect(status().isCreated());

        String completed = reserve(courtA, 18, 2);
        String noShow = reserve(courtA, 8, 1);
        reserve(courtB, 19, 1);
        String cancelled = reserve(courtB, 10, 1);

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", cancelled), owner))
                .andExpect(status().isOk());

        // Ya pasó el día: la sede registra los resultados
        clock.setInstant(OffsetDateTime.parse(at(21)).toInstant());
        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/complete", completed), owner))
                .andExpect(status().isOk());
        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/no-show", noShow), owner))
                .andExpect(status().isOk());
        clock.reset();
    }

    @Test
    void summaryComputesTotalsAndPerCourtMetrics() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reports/summary", venueId), owner)
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueName").value("Sede reportes"))
                .andExpect(jsonPath("$.totals.reservations").value(4))
                .andExpect(jsonPath("$.totals.confirmed").value(1))
                .andExpect(jsonPath("$.totals.completed").value(1))
                .andExpect(jsonPath("$.totals.cancelled").value(1))
                .andExpect(jsonPath("$.totals.noShow").value(1))
                .andExpect(jsonPath("$.totals.bookedHours").value(4))
                .andExpect(jsonPath("$.totals.availableHours").value(26))
                .andExpect(jsonPath("$.totals.occupancyRate").value(0.1538))
                .andExpect(jsonPath("$.totals.realizedRevenue").value(180000))
                .andExpect(jsonPath("$.totals.projectedRevenue").value(90000))
                .andExpect(jsonPath("$.totals.cancellationRate").value(0.25))
                .andExpect(jsonPath("$.totals.noShowRate").value(0.5))
                // Por cancha, ordenadas por nombre
                .andExpect(jsonPath("$.courts[0].courtName").value("Cancha A"))
                .andExpect(jsonPath("$.courts[0].metrics.bookedHours").value(3))
                .andExpect(jsonPath("$.courts[0].metrics.availableHours").value(14))
                .andExpect(jsonPath("$.courts[1].courtName").value("Cancha B"))
                .andExpect(jsonPath("$.courts[1].metrics.availableHours").value(12)) // 14 - 2 bloqueadas
                .andExpect(jsonPath("$.courts[1].metrics.projectedRevenue").value(90000));
    }

    @Test
    void dailySeriesIncludesDaysWithoutReservations() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reports/daily", venueId), owner)
                        .param("from", date.minusDays(1).toString()).param("to", date.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value(date.minusDays(1).toString()))
                .andExpect(jsonPath("$.days[0].metrics.reservations").value(0))
                .andExpect(jsonPath("$.days[0].metrics.availableHours").value(28))
                .andExpect(jsonPath("$.days[1].metrics.bookedHours").value(4))
                .andExpect(jsonPath("$.days[1].metrics.availableHours").value(26))
                .andExpect(jsonPath("$.days[1].metrics.realizedRevenue").value(180000));
    }

    @Test
    void demandCountsEveryOccupiedHourAndIgnoresCancellations() throws Exception {
        int dayOfWeek = date.getDayOfWeek().getValue();

        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reports/demand", venueId), owner)
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk())
                // 08 (A), 18 (A), 19 (A y B); la cancelada de las 10 no cuenta
                .andExpect(jsonPath("$.cells.length()").value(3))
                .andExpect(jsonPath("$.peakHours[0].dayOfWeek").value(dayOfWeek))
                .andExpect(jsonPath("$.peakHours[0].hour").value(19))
                .andExpect(jsonPath("$.peakHours[0].bookedHours").value(2));
    }

    @Test
    void onlyOwnerOrSuperAdminCanSeeVenueReports() throws Exception {
        String path = "/api/v1/venues/{id}/reports/summary";

        mockMvc.perform(withToken(get(path, venueId), adminToken())
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENUE_NOT_OWNED"));
        mockMvc.perform(withToken(get(path, venueId), customerToken())
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path, venueId).param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get(path, venueId), superAdminToken())
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reports/summary", venueId), owner)
                        .param("from", date.toString()).param("to", date.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reports/daily", venueId), owner)
                        .param("from", date.minusDays(400).toString()).param("to", date.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RANGE_TOO_LARGE"));
    }

    @Test
    void platformSummaryIsOnlyForSuperAdmin() throws Exception {
        String month = date.toString().substring(0, 7);

        mockMvc.perform(withToken(get("/api/v1/admin/reports/summary"), superAdminToken())
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.activeVenues").isNumber())
                .andExpect(jsonPath("$.reservations.completed").isNumber())
                // La ocupación es por sede: a nivel plataforma se omite
                .andExpect(jsonPath("$.reservations.availableHours").doesNotExist())
                .andExpect(jsonPath("$.reservations.occupancyRate").doesNotExist())
                .andExpect(jsonPath("$.months[0].month").value(month))
                .andExpect(jsonPath("$.topVenues").isArray());

        mockMvc.perform(withToken(get("/api/v1/admin/reports/summary"), owner)
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isForbidden());
    }

    private void setSchedule(String courtId) throws Exception {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            slots.add(Map.of("dayOfWeek", day, "startHour", 8, "endHour", 18, "pricePerHour", 60000));
            slots.add(Map.of("dayOfWeek", day, "startHour", 18, "endHour", 22, "pricePerHour", 90000));
        }
        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", slots))))
                .andExpect(status().isOk());
    }

    /** Cada reserva la hace un cliente distinto. */
    private String reserve(String courtId, int hour, int hours) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", courtId, "startAt", at(hour), "durationHours", hours))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

    private String at(int hour) {
        return "%sT%02d:00:00-05:00".formatted(date, hour);
    }

}
