package com.sinteticas.api.reservation;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.court.CourtService;
import com.sinteticas.api.holiday.HolidayCalendar;
import com.sinteticas.api.reservation.dto.AvailabilityResponse;
import com.sinteticas.api.reservation.dto.AvailabilityResponse.Slot;
import com.sinteticas.api.reservation.dto.AvailabilityResponse.SlotStatus;
import com.sinteticas.api.schedule.BlockService;
import com.sinteticas.api.schedule.ScheduleBlock;
import com.sinteticas.api.schedule.ScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Calcula los espacios de una hora de una cancha en un día: horario − reservas − bloqueos − horas pasadas.
 * Es lo que el frontend muestra para que el cliente elija.
 */
@Service
public class AvailabilityService {

    private final CourtService courtService;
    private final ScheduleService scheduleService;
    private final BlockService blockService;
    private final ReservationRepository reservationRepository;
    private final BusinessTime time;
    private final ReservationProperties properties;
    private final HolidayCalendar holidayCalendar;

    public AvailabilityService(CourtService courtService, ScheduleService scheduleService, BlockService blockService,
                               ReservationRepository reservationRepository, BusinessTime time,
                               ReservationProperties properties, HolidayCalendar holidayCalendar) {
        this.courtService = courtService;
        this.scheduleService = scheduleService;
        this.blockService = blockService;
        this.reservationRepository = reservationRepository;
        this.time = time;
        this.properties = properties;
        this.holidayCalendar = holidayCalendar;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse forDay(UUID courtId, LocalDate date, Optional<AuthenticatedUser> viewer) {
        Court court = courtService.getVisible(courtId, viewer);
        if (date.isAfter(time.today().plusDays(properties.maxDaysAhead()))) {
            throw ApiException.businessRule("TOO_FAR_AHEAD",
                    "Solo se puede consultar hasta " + properties.maxDaysAhead() + " días adelante");
        }

        Instant dayStart = time.atHour(date, 0);
        Instant dayEnd = time.atHour(date, 24);
        List<Reservation> reservations = reservationRepository.findOccupying(court.getId(), dayStart, dayEnd);
        List<ScheduleBlock> blocks = blockService.findOverlapping(court.getId(), dayStart, dayEnd);
        Instant now = time.now();

        List<Slot> slots = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : scheduleService.hourlyPrices(court.getId(), date).entrySet()) {
            int hour = entry.getKey();
            Instant slotStart = time.atHour(date, hour);
            Instant slotEnd = time.atHour(date, hour + 1);
            slots.add(new Slot(hour, time.toOffset(slotStart), time.toOffset(slotEnd), entry.getValue(),
                    statusOf(slotStart, slotEnd, now, reservations, blocks)));
        }
        return new AvailabilityResponse(court.getId(), date, holidayCalendar.holidayName(date).orElse(null), slots);
    }

    private static SlotStatus statusOf(Instant start, Instant end, Instant now,
                                       List<Reservation> reservations, List<ScheduleBlock> blocks) {
        if (!start.isAfter(now)) {
            return SlotStatus.PAST;
        }
        boolean booked = reservations.stream()
                .anyMatch(r -> r.getStartAt().isBefore(end) && r.getEndAt().isAfter(start));
        if (booked) {
            return SlotStatus.BOOKED;
        }
        boolean blocked = blocks.stream().anyMatch(b -> b.overlaps(start, end));
        return blocked ? SlotStatus.BLOCKED : SlotStatus.AVAILABLE;
    }

}
