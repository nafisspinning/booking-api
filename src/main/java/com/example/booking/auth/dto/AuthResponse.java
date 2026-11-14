package com.example.booking.auth.dto;


public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn      // detik token will expire
) {}
