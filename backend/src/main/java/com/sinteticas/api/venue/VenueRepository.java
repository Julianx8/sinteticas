package com.sinteticas.api.venue;

import com.sinteticas.api.court.CourtType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Page<Venue> findByOwnerId(UUID ownerId, Pageable pageable);

}
