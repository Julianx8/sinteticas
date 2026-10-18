package com.sinteticas.api.schedule;

import com.sinteticas.api.auth.AuthenticatedUser;
import com.sinteticas.api.common.error.ApiException;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.court.CourtService;
import com.sinteticas.api.holiday.HolidayCalendar;
import com.sinteticas.api.schedule.dto.ScheduleSlotRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final CourtScheduleRepository scheduleRepository;
    private final CourtService courtService;
    private final HolidayCalendar holidayCalendar;

    public ScheduleService(CourtScheduleRepository scheduleRepository, CourtService courtService,
                           HolidayCalendar holidayCalendar) {
        this.scheduleRepository = scheduleRepository;
        this.courtService = courtService;
        this.holidayCalendar = holidayCalendar;
    }

    @Transactional(readOnly = true)
    public List<CourtSchedule> getWeekly(UUID courtId, Optional<AuthenticatedUser> viewer) {
        courtService.getVisible(courtId, viewer);
        return scheduleRepository.findByCourtIdOrderByDayOfWeekAscStartHourAsc(courtId);
    }

    /**
     * Reemplaza el horario semanal completo de la cancha.
     * Las reservas ya hechas conservan su precio aunque el horario cambie.
     */
    @Transactional
    public List<CourtSchedule> replaceWeekly(AuthenticatedUser user, UUID courtId, List<ScheduleSlotRequest> slots) {
        Court court = courtService.getManageable(courtId, user);
        validate(slots);

        scheduleRepository.deleteByCourtId(court.getId());
        List<CourtSchedule> schedules = slots.stream()
                .map(s -> new CourtSchedule(court.getId(), s.dayOfWeek(), s.startHour(), s.endHour(), s.pricePerHour()))
                .toList();
        scheduleRepository.saveAllAndFlush(schedules);
        return scheduleRepository.findByCourtIdOrderByDayOfWeekAscStartHourAsc(court.getId());
    }

    /**
     * Precio de cada hora en que la cancha abre ese día: clave = hora de inicio (0-23).
     * Una hora que no aparece en el mapa es una hora en que la cancha está cerrada.
     * En un festivo rige el horario del domingo (ver {@link HolidayCalendar}).
     */
    @Transactional(readOnly = true)
    public Map<Integer, BigDecimal> hourlyPrices(UUID courtId, LocalDate date) {
        Map<Integer, BigDecimal> prices = new TreeMap<>();
        for (CourtSchedule schedule : scheduleRepository.findByCourtIdAndDayOfWeekOrderByStartHour(
                courtId, holidayCalendar.scheduleDayOfWeek(date))) {
            for (int hour = schedule.getStartHour(); hour < schedule.getEndHour(); hour++) {
                prices.put(hour, schedule.getPricePerHour());
            }
        }
        return prices;
    }

    /** Valida antes de tocar la base: así el cliente recibe un mensaje claro en vez de un error de restricción. */
    private static void validate(List<ScheduleSlotRequest> slots) {
        for (ScheduleSlotRequest slot : slots) {
            if (slot.endHour() <= slot.startHour()) {
                throw ApiException.badRequest("INVALID_SCHEDULE_RANGE",
                        "La hora de cierre debe ser mayor que la de apertura (día " + slot.dayOfWeek() + ")");
            }
        }

        Map<Integer, List<ScheduleSlotRequest>> byDay = slots.stream()
                .collect(Collectors.groupingBy(ScheduleSlotRequest::dayOfWeek));
        for (List<ScheduleSlotRequest> daySlots : byDay.values()) {
            List<ScheduleSlotRequest> sorted = daySlots.stream()
                    .sorted(Comparator.comparing(ScheduleSlotRequest::startHour))
                    .toList();
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).startHour() < sorted.get(i - 1).endHour()) {
                    throw ApiException.businessRule("SCHEDULE_OVERLAP",
                            "Hay franjas que se solapan el día " + sorted.get(i).dayOfWeek());
                }
            }
        }
    }

}
