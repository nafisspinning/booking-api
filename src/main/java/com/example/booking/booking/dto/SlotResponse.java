package com.example.booking.booking.dto;
import java.time.LocalTime;

public record SlotResponse(
    LocalTime start, 
    LocalTime end, 
    boolean available
) {}

