package com.sinteticas.api.report;

import com.sinteticas.api.common.config.BusinessTime;
import com.sinteticas.api.court.Court;
import com.sinteticas.api.schedule.CourtSchedule;
import com.sinteticas.api.schedule.CourtScheduleRepository;
import com.sinteticas.api.schedule.ScheduleBlock;
import com.sinteticas.api.schedule.ScheduleBlockRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horas en que una cancha se podía reservar cada día: las de su horario semanal menos las bloqueadas.
 * Es el denominador de la ocupación.
 * <p>
 * Usa el horario y el estado actuales de la cancha: una cancha desactivada hoy cuenta 0 horas
 * disponibles también en fechas pasadas (no guardamos historial de horarios).
 */
@Component
public class AvailableHoursCalculator {

    private final CourtScheduleRepository scheduleRepository;
    private final ScheduleBlockRepository blockRepository;
    private final BusinessTime time;

    public AvailableHoursCalculator(CourtScheduleRepository scheduleRepository,
                                    ScheduleBlockRepository blockRepository, BusinessTime time) {
        this.scheduleRepository = scheduleRepository;
        this.blockRepository = blockRepository;
        this.time = time;
    }

    /** Horas disponibles por día del rango (todos los días aparecen, con 0 si no abre). */
    public Map<LocalDate, Long> perDay(Court court, ReportRange range) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        range.dates().forEach(date -> result.put(date, 0L));
        if (!court.isBookable()) {
            return result;
        }

        // Dos consultas por cancha para todo el rango (no una por día)
        List<CourtSchedule> schedules = scheduleRepository.findByCourtIdOrderByDayOfWeekAscStartHourAsc(court.getId());
        List<ScheduleBlock> blocks = blockRepository.findOverlapping(court.getId(), range.start(), range.end());

        for (LocalDate date : range.dates()) {
            int dayOfWeek = date.getDayOfWeek().getValue();
            long hours = 0;
            for (CourtSchedule schedule : schedules) {
                if (schedule.getDayOfWeek() != dayOfWeek) {
                    continue;
                }
                for (int hour = schedule.getStartHour(); hour < schedule.getEndHour(); hour++) {
                    Instant slotStart = time.atHour(date, hour);
                    Instant slotEnd = time.atHour(date, hour + 1);
                    boolean blocked = blocks.stream().anyMatch(b -> b.overlaps(slotStart, slotEnd));
                    if (!blocked) {
                        hours++;
                    }
                }
            }
            result.put(date, hours);
        }
        return result;
    }

}
