package com.sinteticas.api.reservation.dto;

import com.sinteticas.api.court.CourtType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Disponibilidad de todas las canchas activas de una sede en un día, en una sola respuesta
 * (en vez de una petición por cancha).
 *
 * @param holidayName nombre del festivo si la fecha lo es (rigen el horario y los precios del domingo)
 */
public record VenueAvailabilityResponse(
        UUID venueId,
        LocalDate date,
        String holidayName,
        List<CourtAvailability> courts
) {

    /** @param availableHours cuántas horas libres le quedan ese día (para mostrarlo sin recorrer los slots) */
    public record CourtAvailability(
            UUID courtId,
            String name,
            CourtType courtType,
            int availableHours,
            List<AvailabilityResponse.Slot> slots
    ) {
    }

}
