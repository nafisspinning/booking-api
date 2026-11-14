package com.example.booking.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

//Class yang digunakan untuk menyimpan properti JWT yang dikonfigurasi di application.yaml. 
//Properti ini mencakup secret key dan waktu hidup (TTL) dari token JWT.
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {
}