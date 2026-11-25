package com.example.booking.payment.dto;

import com.example.booking.payment.Payment;
import com.example.booking.payment.PaymentStatus;
import java.math.BigDecimal;

public record PaymentResponse(String bookingCode, BigDecimal amount, PaymentStatus status,
                              String snapToken, String redirectUrl) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getBooking().getCode(), p.getAmount(), p.getStatus(),
                p.getSnapToken(), p.getRedirectUrl());
    }
}