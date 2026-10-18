package com.sinteticas.api.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.sinteticas.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationNotificationIntegrationTest extends IntegrationTest {

    private String owner;
    private String ownerEmail;
    private String courtId;
    private LocalDate date;

    @BeforeEach
    void setUpCourt() throws Exception {
        owner = adminToken();
        ownerEmail = readJson(mockMvc.perform(withToken(get("/api/v1/users/me"), owner))
                .andReturn().getResponse().getContentAsString()).get("email").asText();
        courtId = createCourt(owner, createVenue(owner, "Sede correos"), "Cancha 1", "FUTBOL_5");
        date = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(7);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            slots.add(Map.of("dayOfWeek", day, "startHour", 8, "endHour", 23, "pricePerHour", 90000));
        }
        mockMvc.perform(withToken(put("/api/v1/courts/{id}/schedules", courtId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("slots", slots))))
                .andExpect(status().isOk());
    }

    @Test
    void newReservationNotifiesCustomerAndVenue() throws Exception {
        String customerEmail = uniqueEmail();
        String customer = registerCustomer(customerEmail).get("accessToken").asText();

        reserve(customer, 19, 2);

        List<SimpleMailMessage> toCustomer = mailSender.sentTo(customerEmail);
        assertThat(toCustomer).hasSize(1);
        assertThat(toCustomer.get(0).getSubject()).isEqualTo("Reserva confirmada: Sede correos");
        assertThat(toCustomer.get(0).getText())
                .contains("Cancha 1")
                .contains("de 19:00 a 21:00")
                .contains("$180.000");

        List<SimpleMailMessage> toOwner = mailSender.sentTo(ownerEmail);
        assertThat(toOwner).hasSize(1);
        assertThat(toOwner.get(0).getSubject()).isEqualTo("Nueva reserva en Sede correos");
        assertThat(toOwner.get(0).getText()).contains(customerEmail);
    }

    @Test
    void customerCancellationNotifiesVenue() throws Exception {
        String customerEmail = uniqueEmail();
        String customer = registerCustomer(customerEmail).get("accessToken").asText();
        String reservationId = reserve(customer, 19, 1);

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Se lesionó un jugador"))))
                .andExpect(status().isOk());

        assertThat(mailSender.sentTo(ownerEmail)).last()
                .satisfies(mail -> {
                    assertThat(mail.getSubject()).isEqualTo("Reserva cancelada en Sede correos");
                    assertThat(mail.getText()).contains("Se lesionó un jugador");
                });
        assertThat(mailSender.sentTo(customerEmail)).hasSize(1); // solo la confirmación inicial
    }

    @Test
    void venueCancellationNotifiesCustomer() throws Exception {
        String customerEmail = uniqueEmail();
        String customer = registerCustomer(customerEmail).get("accessToken").asText();
        String reservationId = reserve(customer, 19, 1);

        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Mantenimiento de la grama"))))
                .andExpect(status().isOk());

        assertThat(mailSender.sentTo(customerEmail)).last()
                .satisfies(mail -> {
                    assertThat(mail.getSubject()).isEqualTo("Tu reserva fue cancelada por la sede");
                    assertThat(mail.getText()).contains("Mantenimiento de la grama");
                });
    }

    @Test
    void rejectedReservationSendsNoEmail() throws Exception {
        String customerEmail = uniqueEmail();
        String customer = registerCustomer(customerEmail).get("accessToken").asText();

        mockMvc.perform(withToken(post("/api/v1/reservations"), customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", courtId, "startAt", at(5), "durationHours", 1))))
                .andExpect(status().isUnprocessableEntity());

        assertThat(mailSender.sentTo(customerEmail)).isEmpty();
    }

    private String reserve(String token, int hour, int hours) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/reservations"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", courtId, "startAt", at(hour), "durationHours", hours))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = readJson(body);
        return json.get("id").asText();
    }

    private String at(int hour) {
        return "%sT%02d:00:00-05:00".formatted(date, hour);
    }

}
