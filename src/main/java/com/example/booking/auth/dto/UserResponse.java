package com.example.booking.auth.dto;

import java.util.UUID;

import com.example.booking.auth.Role;
import com.example.booking.auth.User;

public record UserResponse(
    UUID id,
    String fullName,
    String email,
    Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole()
        );
    }
}
