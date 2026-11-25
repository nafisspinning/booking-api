package com.example.booking.payment.midtrans;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.midtrans")
public record MidtransProperties(String serverKey, String snapBaseUrl) {
}