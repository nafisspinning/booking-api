package com.example.booking.auth.dto;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.booking.auth.Role;
import com.example.booking.auth.TokenService;
import com.example.booking.auth.User;
import com.example.booking.auth.UserRepository;
import com.example.booking.common.exception.ConflictException;
import com.example.booking.common.exception.NotFoundException;
import com.example.booking.common.exception.UnAuthorizedException;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional 
    public AuthResponse register(RegisterRequest request) {
        String email = request.email();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN" , "Email already exists");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userRepository.save(user);
        return toAuthResponse(user);
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                        .findByEmailIgnoreCase(request.email().trim())
                        .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                        .orElseThrow(() -> new UnAuthorizedException("email or password is incorrect"));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        UserResponse user = userRepository.findById(userId)
                        .map(UserResponse::from)
                        .orElseThrow(() -> new NotFoundException("User not found"));
        return user;
    }

    private AuthResponse toAuthResponse(User user){
        return new AuthResponse(
            tokenService.issue(user), 
            "Bearer",
            tokenService.ttlSeconds()
        );
    }
}
