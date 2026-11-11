package com.example.booking.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    // menentukan zona waktu default untuk aplikasi, dalam hal ini Asia/Jakarta
    public static final ZoneId VENUE_ZONE = ZoneId.of("Asia/Jakarta");
    @Bean
    public Clock clock() {
        return Clock.system(VENUE_ZONE);
    }
}