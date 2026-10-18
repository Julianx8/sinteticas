package com.sinteticas.api.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CourtScheduleRepository extends JpaRepository<CourtSchedule, UUID> {

    List<CourtSchedule> findByCourtIdOrderByDayOfWeekAscStartHourAsc(UUID courtId);

    List<CourtSchedule> findByCourtIdAndDayOfWeekOrderByStartHour(UUID courtId, int dayOfWeek);

    // Borrado inmediato en SQL: las filas nuevas no deben chocar con las viejas en ex_schedules_no_overlap
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CourtSchedule s where s.courtId = :courtId")
    void deleteByCourtId(@Param("courtId") UUID courtId);

}
