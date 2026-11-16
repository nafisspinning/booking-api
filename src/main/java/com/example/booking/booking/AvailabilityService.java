package com.example.booking.booking;

import com.example.booking.booking.dto.SlotResponse;
import com.example.booking.common.exception.NotFoundException;
import com.example.booking.venue.Court;
import com.example.booking.venue.CourtRepository;
import com.example.booking.venue.Venue;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public List<SlotResponse> getSlots(UUID courtId, LocalDate date) {
        Court court = courtRepository.findWithVenueById(courtId)
                .orElseThrow(() -> new NotFoundException("Lapangan %s tidak ditemukan".formatted(courtId)));
        Venue venue = court.getVenue();

        List<Booking> taken = bookingRepository
                .findByCourtIdAndBookingDateAndStatusIn(courtId, date, BookingStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now(clock);

        List<SlotResponse> slots = new ArrayList<>();
        for (LocalTime start = venue.getOpenTime();
             start.isBefore(venue.getCloseTime());
             start = start.plusHours(1)) {

            LocalTime end = start.plusHours(1);
            LocalTime s = start; // variabel effectively final untuk lambda
            boolean isPast = !LocalDateTime.of(date, start).isAfter(now);
            boolean isTaken = taken.stream()
                    .anyMatch(b -> b.getStartTime().isBefore(end) && b.getEndTime().isAfter(s));

            slots.add(new SlotResponse(start, end, court.isActive() && !isPast && !isTaken));
        }
        return slots;
    }
}