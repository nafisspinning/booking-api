package com.example.booking.payment;

import com.example.booking.booking.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @UuidGenerator (style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private String providerRef;
    private String snapToken;
    private String redirectUrl;
    private Instant paidAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public void markPaid(String providerRef, Instant paidAt) {
        this.status = PaymentStatus.PAID;
        this.providerRef = providerRef;
        this.paidAt = paidAt;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }
}