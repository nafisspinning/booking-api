package com.example.booking.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    @Query("select p from Payment p join fetch p.booking b where b.code = :code")
    Optional<Payment> findByBookingCode(String code);
}