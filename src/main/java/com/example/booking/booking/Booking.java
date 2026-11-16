package com.example.booking.booking;

import com.example.booking.auth.User;
import com.example.booking.common.exception.BusinessException;
import com.example.booking.venue.Court;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id")
    private Court court;

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    private Instant expiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public LocalDateTime startDateTime() {
        return LocalDateTime.of(bookingDate, startTime);
    }

    public void confirm() {
        requireStatus(BookingStatus.PENDING_PAYMENT);
        status = BookingStatus.CONFIRMED;
        expiresAt = null;
    }

    public void expire() {
        requireStatus(BookingStatus.PENDING_PAYMENT);
        status = BookingStatus.EXPIRED;
    }

    public void cancel() {
        if (!BookingStatus.ACTIVE.contains(status)) {
            throw new BusinessException("INVALID_STATUS",
                    "Booking berstatus %s tidak bisa dibatalkan".formatted(status));
        }
        status = BookingStatus.CANCELLED;
    }

    private void requireStatus(BookingStatus expected) {
        if (status != expected) {
            throw new BusinessException("INVALID_STATUS",
                    "Status booking %s, seharusnya %s".formatted(status, expected));
        }
    }
}