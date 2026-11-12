package com.example.booking.venue.dto;

import com.example.booking.venue.Court;
import com.example.booking.venue.SportType;
import java.math.BigDecimal;
import java.util.UUID;

public record CourtResponse(UUID id, UUID venueId, String venueName, String name,
                            SportType sportType, BigDecimal pricePerHour, boolean active) {

    public static CourtResponse from(Court c) {
        return new CourtResponse(c.getId(), c.getVenue().getId(), c.getVenue().getName(),
                c.getName(), c.getSportType(), c.getPricePerHour(), c.isActive());
    }
}