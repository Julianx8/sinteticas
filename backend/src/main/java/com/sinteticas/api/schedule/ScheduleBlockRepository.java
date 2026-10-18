package com.sinteticas.api.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, UUID> {

    /** Bloqueos que se cruzan con el intervalo [from, to). */
    @Query("""
            select b from ScheduleBlock b
            where b.courtId = :courtId and b.startAt < :to and b.endAt > :from
            order by b.startAt
            """)
    List<ScheduleBlock> findOverlapping(@Param("courtId") UUID courtId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    Optional<ScheduleBlock> findByIdAndCourtId(UUID id, UUID courtId);

}
