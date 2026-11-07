package com.sinteticas.api.reservation;

import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.reservation.dto.ReservationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Recordatorio por correo antes del partido ({@code app.reservations.reminder-before}, 3 h por defecto).
 * <p>
 * Marca cada reserva con {@code reminder_sent_at} en la misma transacción en que publica el evento:
 * el correo sale después del commit ({@code ReservationNotifier}), así que una reserva recibe un solo
 * recordatorio aunque la tarea se ejecute muchas veces.
 */
@Service
public class ReservationReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReservationReminderService.class);

    private final ReservationRepository reservationRepository;
    private final ReservationProperties properties;
    private final BusinessTime time;
    private final ApplicationEventPublisher events;

    public ReservationReminderService(ReservationRepository reservationRepository, ReservationProperties properties,
                                      BusinessTime time, ApplicationEventPublisher events) {
        this.reservationRepository = reservationRepository;
        this.properties = properties;
        this.time = time;
        this.events = events;
    }

    /** Envía los recordatorios pendientes. Devuelve cuántos envió. */
    @Transactional
    public int sendDueReminders() {
        Instant now = time.now();
        List<Reservation> due = reservationRepository.findDueForReminder(now, now.plus(properties.reminderBefore()));
        for (Reservation reservation : due) {
            reservation.markReminderSent(now);
            events.publishEvent(new ReservationEvents.ReminderDue(ReservationResponse.from(reservation, time)));
        }
        if (!due.isEmpty()) {
            log.info("Recordatorios de partido enviados: {}", due.size());
        }
        return due.size();
    }

    /** Ejecuta {@link #sendDueReminders()} periódicamente (se apaga con app.reservations.reminders-scheduled=false). */
    @Component
    @ConditionalOnProperty(name = "app.reservations.reminders-scheduled", havingValue = "true", matchIfMissing = true)
    static class Scheduler {

        private final ReservationReminderService service;

        Scheduler(ReservationReminderService service) {
            this.service = service;
        }

        @Scheduled(fixedDelayString = "${app.reservations.reminder-check-interval:PT5M}", initialDelayString = "PT1M")
        void run() {
            service.sendDueReminders();
        }
    }

}
