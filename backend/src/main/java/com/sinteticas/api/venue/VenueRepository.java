package com.sinteticas.api.venue;

import com.sinteticas.api.court.CourtType;
import org.springframework.data.domain.Page;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    /** Catálogo público: solo sedes activas, con filtros opcionales (null = sin filtro). */
    @Query("""
            select v from Venue v
            where v.active = true
              and (:q is null
                   or lower(v.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(v.address) like lower(concat('%', cast(:q as string), '%')))
              and (:neighborhood is null or lower(v.neighborhood) = lower(cast(:neighborhood as string)))
              and (:courtType is null or exists (
                   select 1 from Court c where c.venue = v and c.active = true and c.courtType = :courtType))
            """)
    Page<Venue> searchActive(@Param("q") String q,
                             @Param("neighborhood") String neighborhood,
                             @Param("courtType") CourtType courtType,
                             Pageable pageable);

    /**
     * Como {@link #searchActive}, pero solo sedes con al menos una cancha activa (del tipo pedido, si se pide)
     * libre en [start, end): el horario la abre ese día a esa hora, no hay reserva activa ni bloqueo.
     * {@code dayOfWeek} es el del horario (un festivo usa el del domingo).
     */
    @Query("""
            select v from Venue v
            where v.active = true
              and (:q is null
                   or lower(v.name) like lower(concat('%', cast(:q as string), '%'))
                   or lower(v.address) like lower(concat('%', cast(:q as string), '%')))
              and (:neighborhood is null or lower(v.neighborhood) = lower(cast(:neighborhood as string)))
              and exists (
                   select 1 from Court c
                   where c.venue = v and c.active = true
                     and (:courtType is null or c.courtType = :courtType)
                     and exists (
                          select 1 from CourtSchedule s
                          where s.courtId = c.id and s.dayOfWeek = :dayOfWeek
                            and s.startHour <= :hour and s.endHour > :hour)
                     and not exists (
                          select 1 from Reservation r
                          where r.court = c
                            and r.status <> com.sinteticas.api.reservation.ReservationStatus.CANCELLED
                            and r.startAt < :end and r.endAt > :start)
                     and not exists (
                          select 1 from ScheduleBlock b
                          where b.courtId = c.id and b.startAt < :end and b.endAt > :start))
            """)
    Page<Venue> searchActiveFreeAt(@Param("q") String q,
                                   @Param("neighborhood") String neighborhood,
                                   @Param("courtType") CourtType courtType,
                                   @Param("dayOfWeek") int dayOfWeek,
                                   @Param("hour") int hour,
                                   @Param("start") Instant start,
                                   @Param("end") Instant end,
                                   Pageable pageable);

    Page<Venue> findByOwnerId(UUID ownerId, Pageable pageable);

}
