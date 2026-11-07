package com.sinteticas.api.court;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {

    List<Court> findByVenueIdOrderByName(UUID venueId);

    List<Court> findByVenueIdAndActiveTrueOrderByName(UUID venueId);

    /** [venueId, courtType] de las canchas activas de esas sedes (para el resumen del catálogo). */
    @Query("select distinct c.venue.id, c.courtType from Court c where c.venue.id in :venueIds and c.active = true")
    List<Object[]> findActiveTypesByVenue(@Param("venueIds") Collection<UUID> venueIds);

    /** [venueId, precio mínimo por hora] entre los horarios de las canchas activas de esas sedes. */
    @Query("""
            select c.venue.id, min(s.pricePerHour) from CourtSchedule s, Court c
            where s.courtId = c.id and c.venue.id in :venueIds and c.active = true
            group by c.venue.id
            """)
    List<Object[]> findMinPriceByVenue(@Param("venueIds") Collection<UUID> venueIds);

    @Query("""
            select count(c) > 0 from Court c
            where c.venue.id = :venueId and lower(c.name) = lower(:name)
              and (:excludeId is null or c.id <> :excludeId)
            """)
    boolean existsByVenueAndName(@Param("venueId") UUID venueId, @Param("name") String name,
                                 @Param("excludeId") UUID excludeId);

}
