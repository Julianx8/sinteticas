package com.sinteticas.api.reservation;

import org.springframework.data.domain.Page;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /** Reservas que ocupan el horario (todas menos las canceladas) y se cruzan con [from, to). */
    @Query("""
            select r from Reservation r
            where r.court.id = :courtId
              and r.status <> com.sinteticas.api.reservation.ReservationStatus.CANCELLED
              and r.startAt < :to and r.endAt > :from
            order by r.startAt
            """)
    List<Reservation> findOccupying(@Param("courtId") UUID courtId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query("""
            select count(r) from Reservation r
            where r.customer.id = :customerId
              and r.status = com.sinteticas.api.reservation.ReservationStatus.CONFIRMED
              and r.startAt > :now
            """)
    long countUpcomingConfirmed(@Param("customerId") UUID customerId, @Param("now") Instant now);

    @Query("""
            select r from Reservation r
            join fetch r.court c join fetch c.venue join fetch r.customer
            where r.id = :id
            """)
    Optional<Reservation> findDetailedById(@Param("id") UUID id);

    @Query(value = """
            select r from Reservation r
            join fetch r.court c join fetch c.venue
            where r.customer.id = :customerId
              and (:status is null or r.status = :status)
              and r.startAt >= :from
            """,
            countQuery = """
            select count(r) from Reservation r
            where r.customer.id = :customerId
              and (:status is null or r.status = :status)
              and r.startAt >= :from
            """)
    Page<Reservation> findForCustomer(@Param("customerId") UUID customerId,
                                      @Param("status") ReservationStatus status,
                                      @Param("from") Instant from,
                                      Pageable pageable);

    @Query(value = """
            select r from Reservation r
            join fetch r.court c join fetch c.venue join fetch r.customer
            where c.venue.id = :venueId
              and r.startAt >= :from and r.startAt < :to
              and (:status is null or r.status = :status)
              and (:courtId is null or c.id = :courtId)
            """,
            countQuery = """
            select count(r) from Reservation r
            where r.court.venue.id = :venueId
              and r.startAt >= :from and r.startAt < :to
              and (:status is null or r.status = :status)
              and (:courtId is null or r.court.id = :courtId)
            """)
    Page<Reservation> findForVenue(@Param("venueId") UUID venueId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   @Param("status") ReservationStatus status,
                                   @Param("courtId") UUID courtId,
                                   Pageable pageable);

}
