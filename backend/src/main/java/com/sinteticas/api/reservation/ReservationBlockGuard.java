package com.sinteticas.api.reservation;

import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.schedule.BlockService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * No se puede bloquear un periodo que ya tiene reservas: la sede debe cancelarlas primero
 * (así el cliente queda notificado por la cancelación y no se encuentra la cancha cerrada).
 */
@Component
public class ReservationBlockGuard implements BlockService.BlockGuard {

    private final ReservationRepository reservationRepository;

    public ReservationBlockGuard(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public void beforeBlock(UUID courtId, Instant start, Instant end) {
        int count = reservationRepository.findOccupying(courtId, start, end).size();
        if (count > 0) {
            throw ApiException.conflict("BLOCK_CONFLICTS_WITH_RESERVATIONS",
                    "Hay " + count + " reserva(s) en ese periodo; cancélalas antes de bloquearlo");
        }
    }

}
