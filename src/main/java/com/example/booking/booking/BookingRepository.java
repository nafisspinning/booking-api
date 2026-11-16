package com.example.booking.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            select case when count(b) > 0 then true else false end
            from Booking b
            where b.court.id = :courtId
              and b.bookingDate = :date
              and b.status in :statuses
              and b.startTime < :end
              and b.endTime > :start
            """)
    boolean existsOverlap(UUID courtId, LocalDate date, LocalTime start, LocalTime end,
                          Collection<BookingStatus> statuses);

    List<Booking> findByCourtIdAndBookingDateAndStatusIn(UUID courtId, LocalDate date,
                                                         Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"court", "court.venue"})
    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"court", "court.venue"})
    Optional<Booking> findWithCourtById(UUID id);
}