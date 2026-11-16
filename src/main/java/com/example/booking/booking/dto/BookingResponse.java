package com.example.booking.booking.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import com.example.booking.booking.Booking;
import com.example.booking.booking.BookingStatus;

public record BookingResponse(
        UUID id, 
        String code, 
        UUID courtId, 
        String courtName, 
        String venueName,
        LocalDate date, 
        LocalTime startTime, 
        LocalTime endTime,
        BigDecimal totalPrice, 
        BookingStatus status, 
        Instant expiresAt
) {    
    public static BookingResponse from(Booking b) {
        return new BookingResponse(
            b.getId(), 
            b.getCode(), 
            b.getCourt().getId(),
            b.getCourt().getName(), 
            b.getCourt().getVenue().getName(),
            b.getBookingDate(), 
            b.getStartTime(), 
            b.getEndTime(),
            b.getTotalPrice(), 
            b.getStatus(), 
            b.getExpiresAt());
    }
}