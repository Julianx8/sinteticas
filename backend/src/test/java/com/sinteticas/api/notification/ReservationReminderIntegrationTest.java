package com.sinteticas.api.notification;

import com.sinteticas.api.IntegrationTest;
import com.sinteticas.api.reservation.ReservationReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recordatorio por correo 3 h antes del partido. La tarea automática está apagada en los tests
 * (reminders-scheduled: false): aquí se llama directamente moviendo el reloj de prueba.
 */
class ReservationReminderIntegrationTest extends IntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Autowired
    private ReservationReminderService reminderService;

    private String courtId;
    private LocalDate date;
    private Instant start;

    @BeforeEach
    void setUpCourt() throws Exception {
        String owner = adminToken();
        courtId = createCourt(owner, createVenue(owner, "Sede recordatorios"), "Cancha 1", "FUTBOL_5");
        date = LocalDate.now(BOGOTA).plusDays(10);
        start = date.atTime(19, 0).atZone(BOGOTA).toInstant();

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
    void reminderIsSentOnceWhenTheMatchIsLessThanThreeHoursAway() throws Exception {
        String email = uniqueEmail();
        String customer = registerCustomer(email).get("accessToken").asText();
        reserve(customer);

        reminderService.sendDueReminders();
        assertThat(reminders(email)).as("10 días antes no hay recordatorio").isEmpty();

        clock.setInstant(start.minus(Duration.ofHours(3)).minusSeconds(60));
        reminderService.sendDueReminders();
        assertThat(reminders(email)).as("un minuto antes de la ventana tampoco").isEmpty();

        clock.setInstant(start.minus(Duration.ofHours(2).plusMinutes(55)));
        reminderService.sendDueReminders();
        reminderService.sendDueReminders(); // la tarea corre muchas veces: no debe repetir el correo
        List<SimpleMailMessage> sent = reminders(email);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getSubject()).isEqualTo("Recordatorio: tu partido es a las 19:00 en Sede recordatorios");
        assertThat(sent.get(0).getText())
                .contains("Cancha 1")
                .contains("de 19:00 a 20:00")
                .contains("https://www.google.com/maps/search/?api=1&query=");
    }

    @Test
    void cancelledReservationGetsNoReminder() throws Exception {
        String email = uniqueEmail();
        String customer = registerCustomer(email).get("accessToken").asText();
        String reservationId = reserve(customer);
        mockMvc.perform(withToken(post("/api/v1/reservations/{id}/cancel", reservationId), customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isOk());

        clock.setInstant(start.minus(Duration.ofHours(1)));
        reminderService.sendDueReminders();

        assertThat(reminders(email)).isEmpty();
    }

    @Test
    void lastMinuteBookingOnlyGetsTheConfirmation() throws Exception {
        clock.setInstant(start.minus(Duration.ofHours(2))); // reserva cuando ya faltan 2 h
        String email = uniqueEmail();
        String customer = registerCustomer(email).get("accessToken").asText();
        reserve(customer);

        reminderService.sendDueReminders();

        assertThat(reminders(email)).isEmpty();
        assertThat(mailSender.sentTo(email)).extracting(SimpleMailMessage::getSubject)
                .containsExactly("Reserva confirmada: Sede recordatorios");
    }

    private List<SimpleMailMessage> reminders(String email) {
        return mailSender.sentTo(email).stream()
                .filter(mail -> mail.getSubject() != null && mail.getSubject().startsWith("Recordatorio"))
                .toList();
    }

    private String reserve(String token) throws Exception {
        String body = mockMvc.perform(withToken(post("/api/v1/reservations"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courtId", courtId, "startAt", date + "T19:00:00-05:00", "durationHours", 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asText();
    }

}
