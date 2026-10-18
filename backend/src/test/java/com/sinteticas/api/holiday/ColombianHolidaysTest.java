package com.sinteticas.api.holiday;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario (sin Spring ni base de datos): compara con el calendario oficial de festivos de Colombia.
 */
class ColombianHolidaysTest {

    @Test
    void easterSundayIsCorrect() {
        assertThat(ColombianHolidays.easterSunday(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(ColombianHolidays.easterSunday(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(ColombianHolidays.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
    }

    @Test
    void holidays2026MatchTheOfficialCalendar() {
        assertThat(dates(2026)).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 3, 23),
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3), LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 15),
                LocalDate.of(2026, 6, 29), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 7),
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 25));
    }

    @Test
    void holidays2025IncludeTwoOnTheSameDay() {
        // 30 de junio de 2025: San Pedro y San Pablo (trasladado) y Sagrado Corazón caen el mismo lunes
        assertThat(ColombianHolidays.forYear(2025)).hasSize(18);
        assertThat(ColombianHolidays.nameOf(LocalDate.of(2025, 6, 30)))
                .hasValue("San Pedro y San Pablo y Sagrado Corazón de Jesús");
        assertThat(ColombianHolidays.isHoliday(LocalDate.of(2025, 6, 29))).isFalse();
    }

    @Test
    void movableHolidaysLandOnMonday() {
        // 1 de noviembre de 2026 es domingo → se traslada al lunes 2
        assertThat(ColombianHolidays.isHoliday(LocalDate.of(2026, 11, 1))).isFalse();
        assertThat(ColombianHolidays.nameOf(LocalDate.of(2026, 11, 2))).hasValue("Día de Todos los Santos");
        // Los fijos no se mueven: 20 de julio de 2025 fue domingo y siguió siendo festivo ese día
        assertThat(ColombianHolidays.isHoliday(LocalDate.of(2025, 7, 20))).isTrue();
    }

    private static List<LocalDate> dates(int year) {
        return ColombianHolidays.forYear(year).stream().map(ColombianHolidays.Holiday::date).toList();
    }

}
