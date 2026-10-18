package com.sinteticas.api.report;

import com.sinteticas.api.reservation.Reservation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de agregación para reportes. Son SQL nativo a propósito: agrupar por día o por hora local
 * y expandir reservas de varias horas (generate_series) es trabajo de PostgreSQL, no de Java.
 * Todas filtran por {@code start_at} en [from, to) y usan los índices de reservations.
 * <p>
 * Ojo: en SQL nativo con parámetros no se usa {@code ::} para castear, sino {@code cast(... as ...)}.
 */
public interface ReportRepository extends Repository<Reservation, UUID> {

    /** Conteo, horas y monto por cancha y estado. */
    interface CourtStatusRow {
        UUID getCourtId();

        String getStatus();

        long getReservations();

        BigDecimal getHours();

        BigDecimal getAmount();
    }

    /** Conteo, horas y monto por día local (YYYY-MM-DD) y estado. */
    interface DayStatusRow {
        String getDay();

        String getStatus();

        long getReservations();

        BigDecimal getHours();

        BigDecimal getAmount();
    }

    /** Horas reservadas por día de la semana (1 = lunes) y hora local. */
    interface DemandRow {
        int getDayOfWeek();

        int getHour();

        long getBookedHours();
    }

    /** Conteo y monto por estado. */
    interface StatusRow {
        String getStatus();

        long getReservations();

        BigDecimal getHours();

        BigDecimal getAmount();
    }

    /** Conteo y monto por mes local (YYYY-MM) y estado. */
    interface MonthStatusRow {
        String getMonth();

        String getStatus();

        long getReservations();

        BigDecimal getAmount();
    }

    interface TopVenueRow {
        UUID getVenueId();

        String getVenueName();

        long getReservations();

        BigDecimal getRealizedRevenue();
    }

    @Query(nativeQuery = true, value = """
            select r.court_id as courtId,
                   r.status as status,
                   count(*) as reservations,
                   coalesce(sum(extract(epoch from (r.end_at - r.start_at)) / 3600), 0) as hours,
                   coalesce(sum(r.total_price), 0) as amount
            from reservations r
            join courts c on c.id = r.court_id
            where c.venue_id = :venueId and r.start_at >= :from and r.start_at < :to
            group by r.court_id, r.status
            """)
    List<CourtStatusRow> venueByCourtAndStatus(@Param("venueId") UUID venueId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            select to_char(timezone(:zone, r.start_at), 'YYYY-MM-DD') as day,
                   r.status as status,
                   count(*) as reservations,
                   coalesce(sum(extract(epoch from (r.end_at - r.start_at)) / 3600), 0) as hours,
                   coalesce(sum(r.total_price), 0) as amount
            from reservations r
            join courts c on c.id = r.court_id
            where c.venue_id = :venueId and r.start_at >= :from and r.start_at < :to
            group by 1, 2
            """)
    List<DayStatusRow> venueByDayAndStatus(@Param("venueId") UUID venueId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           @Param("zone") String zone);

    /**
     * Una reserva de 18:00 a 20:00 cuenta en las celdas de las 18 y de las 19 (generate_series),
     * así el mapa de demanda refleja las horas realmente ocupadas.
     */
    @Query(nativeQuery = true, value = """
            select cast(extract(isodow from timezone(:zone, h.slot)) as int) as dayOfWeek,
                   cast(extract(hour from timezone(:zone, h.slot)) as int) as hour,
                   count(*) as bookedHours
            from reservations r
            join courts c on c.id = r.court_id
            cross join lateral generate_series(r.start_at, r.end_at - interval '1 hour', interval '1 hour') as h(slot)
            where c.venue_id = :venueId
              and r.status <> 'CANCELLED'
              and r.start_at >= :from and r.start_at < :to
            group by 1, 2
            order by 1, 2
            """)
    List<DemandRow> venueDemand(@Param("venueId") UUID venueId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                @Param("zone") String zone);

    @Query(nativeQuery = true, value = """
            select r.status as status,
                   count(*) as reservations,
                   coalesce(sum(extract(epoch from (r.end_at - r.start_at)) / 3600), 0) as hours,
                   coalesce(sum(r.total_price), 0) as amount
            from reservations r
            where r.start_at >= :from and r.start_at < :to
            group by r.status
            """)
    List<StatusRow> platformByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = """
            select to_char(timezone(:zone, r.start_at), 'YYYY-MM') as month,
                   r.status as status,
                   count(*) as reservations,
                   coalesce(sum(r.total_price), 0) as amount
            from reservations r
            where r.start_at >= :from and r.start_at < :to
            group by 1, 2
            """)
    List<MonthStatusRow> platformByMonthAndStatus(@Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("zone") String zone);

    @Query(nativeQuery = true, value = """
            select v.id as venueId,
                   v.name as venueName,
                   count(*) as reservations,
                   coalesce(sum(case when r.status = 'COMPLETED' then r.total_price else 0 end), 0) as realizedRevenue
            from reservations r
            join courts c on c.id = r.court_id
            join venues v on v.id = c.venue_id
            where r.status <> 'CANCELLED' and r.start_at >= :from and r.start_at < :to
            group by v.id, v.name
            order by reservations desc, v.name
            limit :limit
            """)
    List<TopVenueRow> platformTopVenues(@Param("from") Instant from,
                                        @Param("to") Instant to,
                                        @Param("limit") int limit);

    @Query(nativeQuery = true, value = """
            select count(*) from users u where u.created_at >= :from and u.created_at < :to
            """)
    long countUsersCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(nativeQuery = true, value = "select count(*) from venues v where v.active")
    long countActiveVenues();

    @Query(nativeQuery = true, value = """
            select count(*) from courts c join venues v on v.id = c.venue_id where c.active and v.active
            """)
    long countActiveCourts();

}
