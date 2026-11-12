package com.example.booking.venue;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}