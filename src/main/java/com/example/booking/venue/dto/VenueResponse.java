package com.example.booking.venue.dto;

import com.example.booking.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;

public record VenueResponse(UUID id, String name, String address,
                            LocalTime openTime, LocalTime closeTime) {

    public static VenueResponse from(Venue v) {
        return new VenueResponse(v.getId(), v.getName(), v.getAddress(),
                v.getOpenTime(), v.getCloseTime());
    }
}