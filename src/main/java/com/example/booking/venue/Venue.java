package com.example.booking.venue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter
@NoArgsConstructor  
@Entity(name = "venues")
public class Venue {

    @Id 
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 120) 
    private String name;

    @Column (nullable = false)
    private String address;

    @Column (nullable = false)
    private LocalTime openTime;

    @Column (nullable = false)
    private LocalTime closeTime;

    @CreationTimestamp 
    @Column (nullable = false, updatable = false)
    private Instant createdAt;
    

    public Boolean isOpenBetween(LocalTime start, LocalTime end) {
        return !start.isBefore(openTime) && !end.isAfter(closeTime);
    }
}
