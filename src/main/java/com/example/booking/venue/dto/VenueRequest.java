package com.example.booking.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record VenueRequest(
        @NotBlank @Size(max = 120) 
        String name,
        
        @NotBlank @Size(max = 255) 
        String address,
        
        @NotNull LocalTime 
        openTime,
        
        @NotNull LocalTime 
        closeTime) {
}