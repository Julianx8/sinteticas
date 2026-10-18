package com.sinteticas.api.court;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {

    List<Court> findByVenueIdOrderByName(UUID venueId);

    List<Court> findByVenueIdAndActiveTrueOrderByName(UUID venueId);

    @Query("""
            select count(c) > 0 from Court c
            where c.venue.id = :venueId and lower(c.name) = lower(:name)
              and (:excludeId is null or c.id <> :excludeId)
            """)
    boolean existsByVenueAndName(@Param("venueId") UUID venueId, @Param("name") String name,
                                 @Param("excludeId") UUID excludeId);

}
