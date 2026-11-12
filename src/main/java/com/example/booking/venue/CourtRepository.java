package com.example.booking.venue;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CourtRepository extends JpaRepository<Court, UUID> {

    @EntityGraph(attributePaths = "venue")
    Page<Court> findByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = "venue")
    Page<Court> findBySportTypeAndActiveTrue(SportType sportType, Pageable pageable);

    @EntityGraph(attributePaths = "venue")
    Optional<Court> findWithVenueById(UUID id);

    boolean existsByVenueIdAndNameIgnoreCase(UUID venueId, String name);

    boolean existsByVenueId(UUID venueId);
}