package com.example.booking.venue.dto;

import com.example.booking.venue.SportType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CourtRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull SportType sportType,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal pricePerHour) {
}