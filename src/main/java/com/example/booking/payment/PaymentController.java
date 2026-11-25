package com.example.booking.payment;

import com.example.booking.payment.dto.PaymentResponse;
import com.example.booking.payment.midtrans.MidtransDtos.Notification;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/bookings/{id}/payment")
    public PaymentResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return paymentService.createPayment(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/api/payments/callback")
    public void callback(@RequestBody Notification notification) {
        paymentService.handleNotification(notification);
    }
}