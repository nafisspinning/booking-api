package com.example.booking.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}