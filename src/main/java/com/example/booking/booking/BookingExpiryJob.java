package com.example.booking.booking;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireUnpaidBookings() {
        List<Booking> overdue = bookingRepository.findTop100ByStatusAndExpiresAtBefore(
                BookingStatus.PENDING_PAYMENT, clock.instant());
        overdue.forEach(Booking::expire);
        if (!overdue.isEmpty()) {
            log.info("Expired {} unpaid bookings", overdue.size());
        }
    }
}