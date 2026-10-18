package com.sinteticas.api.notification;

import com.sinteticas.api.reservation.ReservationEvents;
import com.sinteticas.api.reservation.dto.ReservationResponse;
import com.sinteticas.api.user.User;
import com.sinteticas.api.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * Correos de reservas. Escucha con {@code AFTER_COMMIT}: solo se avisa de lo que de verdad quedó guardado
 * (si la transacción falla, no sale ningún correo).
 * <ul>
 *   <li>Reserva creada → confirmación al cliente + aviso a la sede.</li>
 *   <li>Cancelada por el cliente → aviso a la sede. Cancelada por la sede → aviso al cliente.</li>
 * </ul>
 */
@Component
public class ReservationNotifier {

    private final MailService mailService;
    private final UserRepository userRepository;

    public ReservationNotifier(MailService mailService, UserRepository userRepository) {
        this.mailService = mailService;
        this.userRepository = userRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(ReservationEvents.Created event) {
        ReservationResponse r = event.reservation();

        mailService.send(r.customer().email(), "Reserva confirmada: " + r.court().venueName(),
                "Hola " + r.customer().fullName() + ",\n\n"
                        + "Tu reserva quedó confirmada.\n\n"
                        + details(r)
                        + "\nEl pago se hace en la sede. Si no puedes ir, cancela con tiempo desde la aplicación.\n\n"
                        + "— Sintéticas");

        ownerEmail(event.venueOwnerId()).ifPresent(email -> mailService.send(email,
                "Nueva reserva en " + r.court().venueName(),
                "Hay una nueva reserva.\n\n"
                        + details(r)
                        + "Cliente: " + r.customer().fullName() + " (" + r.customer().email() + ")\n"
                        + (r.notes() == null ? "" : "Notas: " + r.notes() + "\n")
                        + "\n— Sintéticas"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(ReservationEvents.Cancelled event) {
        ReservationResponse r = event.reservation();
        String reason = r.cancellationReason() == null ? "" : "Motivo: " + r.cancellationReason() + "\n";

        if (event.byVenue()) {
            mailService.send(r.customer().email(), "Tu reserva fue cancelada por la sede",
                    "Hola " + r.customer().fullName() + ",\n\n"
                            + "La sede canceló tu reserva.\n\n"
                            + details(r) + reason
                            + "\nDisculpa las molestias. Puedes buscar otro horario en la aplicación.\n\n"
                            + "— Sintéticas");
        } else {
            ownerEmail(event.venueOwnerId()).ifPresent(email -> mailService.send(email,
                    "Reserva cancelada en " + r.court().venueName(),
                    "El cliente canceló su reserva; el horario quedó libre.\n\n"
                            + details(r)
                            + "Cliente: " + r.customer().fullName() + " (" + r.customer().email() + ")\n"
                            + reason
                            + "\n— Sintéticas"));
        }
    }

    private static String details(ReservationResponse r) {
        return "Sede: " + r.court().venueName() + " — " + r.court().venueAddress() + "\n"
                + "Cancha: " + r.court().name() + "\n"
                + "Fecha: " + EmailFormat.slot(r.startAt(), r.endAt()) + "\n"
                + "Valor: " + EmailFormat.money(r.totalPrice()) + "\n";
    }

    private Optional<String> ownerEmail(UUID ownerId) {
        return userRepository.findById(ownerId).filter(User::isEnabled).map(User::getEmail);
    }

}
