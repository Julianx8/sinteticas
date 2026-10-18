package com.sinteticas.api.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationIntegrationTest extends IntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private String owner;
    private String venueId;
    private String courtId;
    private LocalDate date;

    /**
     * Cancha abierta todos los días: 06-18 a $60.000 y 18-24 a $90.000.
     * Las reservas se hacen dentro de una semana para que siempre estén en el futuro.
     */
    @BeforeEach
    void setUpCourt() throws Exception {
        owner = adminToken();
        venueId = createVenue(owner, "Sede reservas");
        courtId = createCourt(owner, venueId, "Cancha 1", "FUTBOL_5");
        date = LocalDate.now(BOGOTA).plusDays(7);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            slots.add(Map.of("dayOfWeek", day, "startHour", 6, "endHour", 18, "pricePerHour", 60000));
            slots.add(Map.of("dayOfWeek", day, "startHour", 18, "endHour", 24, "pricePerHour", 90000));
        }
        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", slots))))
                .andExpect(status().isOk());
    }

    @Test
    void reservationPriceSumsEachHourAcrossPriceBands() throws Exception {
        String customer = customerToken();

        mockMvc.perform(withToken(post("/api/v1/reservations"), customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reservationBody(17, 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.totalPrice").value(150000)) // 17-18 a 60.000 + 18-19 a 90.000
                .andExpect(jsonPath("$.startAt").value(at(17)))
                .andExpect(jsonPath("$.court.venueName").value("Sede reservas"));
    }

    @Test
    void availabilityShowsPricesAndBookedSlots() throws Exception {
        reserve(customerToken(), 20, 1);

        mockMvc.perform(get("/api/v1/courts/{id}/availability", courtId).param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(18)) // de 6 a 24
                .andExpect(jsonPath("$.slots[0].hour").value(6))
                .andExpect(jsonPath("$.slots[0].price").value(60000))
                .andExpect(jsonPath("$.slots[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.slots[14].hour").value(20))
                .andExpect(jsonPath("$.slots[14].status").value("BOOKED"));
    }

    @Test
    void overlappingReservationIsRejectedButBackToBackIsAllowed() throws Exception {
        reserve(customerToken(), 18, 2); // 18-20

        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reservationBody(19, 1)))) // 19-20 se cruza
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_SLOT_TAKEN"));

        reserve(customerToken(), 20, 1); // 20-21 empieza justo cuando termina la otra
    }

    /**
     * Varias personas intentan reservar el mismo horario exactamente al mismo tiempo.
     * La restricción EXCLUDE de PostgreSQL garantiza que solo una lo logra.
     */
    @Test
    void concurrentReservationsForSameSlotOnlyOneWins() throws Exception {
        int attempts = 8;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tokens.add(customerToken());
        }

        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : tokens) {
                Callable<Integer> attempt = () -> {
                    startSignal.await();
                    return mockMvc.perform(withToken(post("/api/v1/reservations"), token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(reservationBody(21, 1))))
                            .andReturn().getResponse().getStatus();
                };
                results.add(executor.submit(attempt));
            }
            startSignal.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(attempts - 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reservationOutsideScheduleIsRejected() throws Exception {
        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reservationBody(4, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OUTSIDE_SCHEDULE"));
    }

    @Test
    void invalidTimingIsRejected() throws Exception {
        String customer = customerToken();

        Map<String, Object> notOnTheHour = reservationBody(18, 1);
        notOnTheHour.put("startAt", date + "T18:30:00-05:00");
        expectError(customer, notOnTheHour, 400, "INVALID_START_TIME");

        expectError(customer, reservationBody(18, 4), 422, "DURATION_TOO_LONG");
        expectError(customer, reservationBody(23, 2), 422, "CROSSES_MIDNIGHT");

        Map<String, Object> past = reservationBody(18, 1);
        past.put("startAt", LocalDate.now(BOGOTA).minusDays(1) + "T18:00:00-05:00");
        expectError(customer, past, 422, "RESERVATION_IN_PAST");

        Map<String, Object> tooFar = reservationBody(18, 1);
        tooFar.put("startAt", LocalDate.now(BOGOTA).plusDays(60) + "T18:00:00-05:00");
        expectError(customer, tooFar, 422, "TOO_FAR_AHEAD");
    }

    @Test
    void startTimeInOtherTimezoneIsInterpretedCorrectly() throws Exception {
        // 23:00 UTC = 18:00 en Colombia
        Map<String, Object> body = reservationBody(18, 1);
        body.put("startAt", date + "T23:00:00Z");

        mockMvc.perform(withToken(post("/api/v1/reservations"), customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value(at(18)))
                .andExpect(jsonPath("$.totalPrice").value(90000));
    }

    @Test
    void blockedSlotCannotBeReservedAndReservedSlotCannotBeBlocked() throws Exception {
        mockMvc.perform(withToken(post("/api/v1/courts/{id}/blocks", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("startAt", at(8), "endAt", at(12), "reason", "Mantenimiento"))))
                .andExpect(status().isCreated());

        expectError(customerToken(), reservationBody(10, 1), 409, "SLOT_BLOCKED");

        reserve(customerToken(), 19, 1);
        mockMvc.perform(withToken(post("/api/v1/courts/{id}/blocks", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("startAt", at(18), "endAt", at(22), "reason", "Torneo"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_CONFLICTS_WITH_RESERVATIONS"));

        mockMvc.perform(get("/api/v1/courts/{id}/availability", courtId).param("date", date.toString()))
                .andExpect(jsonPath("$.slots[4].status").value("BLOCKED")); // 10:00
    }

    @Test
    void customerCancelsAndSlotBecomesAvailableAgain() throws Exception {
        String customer = customerToken();
        String reservationId = reserve(customer, 18, 1);

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "No puedo ir"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("No puedo ir"));

        // Otra persona puede reservar ese horario
        reserve(customerToken(), 18, 1);

        // Cancelar dos veces no se puede
        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), customer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_ACTIVE"));
    }

    @Test
    void customerCannotCancelTooLateButVenueCan() throws Exception {
        String customer = customerToken();
        String reservationId = reserve(customer, 18, 1);

        // Simulamos que falta 1 hora para el inicio (el límite es 2 horas)
        clock.setInstant(OffsetDateTime.parse(at(17)).toInstant());

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), customer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CANCELLATION_TOO_LATE"));

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void venueMarksOutcomeOnlyAfterStart() throws Exception {
        String reservationId = reserve(customerToken(), 18, 1);

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/complete", reservationId), owner))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_STARTED"));

        clock.setInstant(OffsetDateTime.parse(at(18)).toInstant().plus(Duration.ofMinutes(30)));

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/no-show", reservationId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void reservationsArePrivate() throws Exception {
        String reservationId = reserve(customerToken(), 18, 1);
        String stranger = customerToken();

        mockMvc.perform(withToken(get("/api/v1/reservations/{id}", reservationId), stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/complete", reservationId), adminToken()))
                .andExpect(status().isNotFound());

        // El dueño de la sede sí la ve
        mockMvc.perform(withToken(get("/api/v1/reservations/{id}", reservationId), owner))
                .andExpect(status().isOk());
    }

    @Test
    void customerListsOwnReservations() throws Exception {
        String customer = customerToken();
        reserve(customer, 8, 1);
        reserve(customer, 19, 1);
        reserve(customerToken(), 21, 1); // de otro cliente

        mockMvc.perform(withToken(get("/api/v1/reservations/mine").param("upcoming", "true"), customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].startAt").value(at(19))); // más reciente primero
    }

    @Test
    void ownerSeesVenueAgendaWithCustomerData() throws Exception {
        reserve(customerToken(), 8, 1);
        reserve(customerToken(), 19, 2);

        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reservations", venueId), owner)
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].startAt").value(at(8)))
                .andExpect(jsonPath("$.content[0].customer.email").exists());

        mockMvc.perform(withToken(get("/api/v1/venues/{id}/reservations", venueId), adminToken())
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotHoardSlots() throws Exception {
        String customer = customerToken();
        for (int hour = 6; hour < 11; hour++) {
            reserve(customer, hour, 1);
        }
        expectError(customer, reservationBody(12, 1), 422, "TOO_MANY_ACTIVE_RESERVATIONS");
    }

    private String at(int hour) {
        return "%sT%02d:00:00-05:00".formatted(date, hour);
    }

    private Map<String, Object> reservationBody(int hour, int durationHours) {
        Map<String, Object> body = new HashMap<>();
        body.put("courtId", courtId);
        body.put("startAt", at(hour));
        body.put("durationHours", durationHours);
        return body;
    }

    private String reserve(String token, int hour, int durationHours) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/reservations"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reservationBody(hour, durationHours))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = readJson(body);
        return json.get("id").asText();
    }

    private void expectError(String token, Map<String, Object> body, int httpStatus, String code) throws Exception {
        mockMvc.perform(withToken(post("/api/v1/reservations"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }

}
