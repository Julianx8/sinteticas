package com.sinteticas.api.holiday;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Decide qué horario semanal aplica a una fecha. En Colombia las canchas suelen cobrar los festivos
 * como domingo: con {@code app.holidays.sunday-schedule=true} (por defecto) un festivo usa las franjas
 * y precios del domingo (dayOfWeek 7). Para cerrar un festivo puntual, la sede crea un bloqueo.
 */
@Component
public class HolidayCalendar {

    private final boolean sundayScheduleOnHolidays;

    public HolidayCalendar(@Value("${app.holidays.sunday-schedule:true}") boolean sundayScheduleOnHolidays) {
        this.sundayScheduleOnHolidays = sundayScheduleOnHolidays;
    }

    /** Día de la semana (ISO, 1 = lunes … 7 = domingo) cuyo horario rige esa fecha. */
    public int scheduleDayOfWeek(LocalDate date) {
        if (sundayScheduleOnHolidays && ColombianHolidays.isHoliday(date)) {
            return DayOfWeek.SUNDAY.getValue();
        }
        return date.getDayOfWeek().getValue();
    }

    public Optional<String> holidayName(LocalDate date) {
        return ColombianHolidays.nameOf(date);
    }

}
