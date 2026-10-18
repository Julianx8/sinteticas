package com.sinteticas.api.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Festivos de Colombia (Ley 51 de 1983, "Ley Emiliani"), calculados para cualquier año:
 * <ul>
 *   <li>Fijos: 1 ene, 1 may, 20 jul, 7 ago, 8 dic, 25 dic.</li>
 *   <li>Trasladables al lunes siguiente: 6 ene, 19 mar, 29 jun, 15 ago, 12 oct, 1 nov, 11 nov.</li>
 *   <li>Según la Pascua: Jueves y Viernes Santo (fijos); Ascensión (+43 días), Corpus Christi (+64) y
 *       Sagrado Corazón (+71), que ya caen en lunes.</li>
 * </ul>
 * Dos festivos pueden caer el mismo día (p. ej. 30 de junio de 2025): se devuelven ambos.
 * Clase sin dependencias de Spring, fácil de probar.
 */
public final class ColombianHolidays {

    public record Holiday(LocalDate date, String name) {
    }

    private record Fixed(MonthDay day, String name, boolean movesToMonday) {
    }

    private static final List<Fixed> FIXED = List.of(
            new Fixed(MonthDay.of(1, 1), "Año Nuevo", false),
            new Fixed(MonthDay.of(1, 6), "Día de los Reyes Magos", true),
            new Fixed(MonthDay.of(3, 19), "Día de San José", true),
            new Fixed(MonthDay.of(5, 1), "Día del Trabajo", false),
            new Fixed(MonthDay.of(6, 29), "San Pedro y San Pablo", true),
            new Fixed(MonthDay.of(7, 20), "Día de la Independencia", false),
            new Fixed(MonthDay.of(8, 7), "Batalla de Boyacá", false),
            new Fixed(MonthDay.of(8, 15), "La Asunción de la Virgen", true),
            new Fixed(MonthDay.of(10, 12), "Día de la Raza", true),
            new Fixed(MonthDay.of(11, 1), "Día de Todos los Santos", true),
            new Fixed(MonthDay.of(11, 11), "Independencia de Cartagena", true),
            new Fixed(MonthDay.of(12, 8), "Día de la Inmaculada Concepción", false),
            new Fixed(MonthDay.of(12, 25), "Navidad", false));

    private static final ConcurrentHashMap<Integer, List<Holiday>> CACHE = new ConcurrentHashMap<>();

    private ColombianHolidays() {
    }

    /** Festivos del año, ordenados por fecha. */
    public static List<Holiday> forYear(int year) {
        return CACHE.computeIfAbsent(year, ColombianHolidays::compute);
    }

    /** Nombre del festivo (o los nombres unidos con " y ", si coinciden dos), o vacío si no es festivo. */
    public static Optional<String> nameOf(LocalDate date) {
        List<String> names = forYear(date.getYear()).stream()
                .filter(h -> h.date().equals(date))
                .map(Holiday::name)
                .toList();
        return names.isEmpty() ? Optional.empty() : Optional.of(String.join(" y ", names));
    }

    public static boolean isHoliday(LocalDate date) {
        return nameOf(date).isPresent();
    }

    private static List<Holiday> compute(int year) {
        List<Holiday> holidays = new ArrayList<>();
        for (Fixed fixed : FIXED) {
            LocalDate date = fixed.day().atYear(year);
            holidays.add(new Holiday(fixed.movesToMonday() ? nextMonday(date) : date, fixed.name()));
        }
        LocalDate easter = easterSunday(year);
        holidays.add(new Holiday(easter.minusDays(3), "Jueves Santo"));
        holidays.add(new Holiday(easter.minusDays(2), "Viernes Santo"));
        holidays.add(new Holiday(easter.plusDays(43), "Ascensión del Señor"));
        holidays.add(new Holiday(easter.plusDays(64), "Corpus Christi"));
        holidays.add(new Holiday(easter.plusDays(71), "Sagrado Corazón de Jesús"));
        holidays.sort(Comparator.comparing(Holiday::date));
        return List.copyOf(holidays);
    }

    /** El mismo día si ya es lunes; si no, el lunes siguiente. */
    private static LocalDate nextMonday(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua, calendario gregoriano (algoritmo anónimo de Meeus/Jones/Butcher). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

}
