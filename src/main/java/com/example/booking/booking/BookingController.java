package com.example.booking.booking;

import com.example.booking.booking.dto.*;
import com.example.booking.common.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final AvailabilityService availabilityService;

    @GetMapping("/api/courts/{courtId}/availability")
    public List<SlotResponse> availability(@PathVariable UUID courtId,
                                           @RequestParam LocalDate date) {
        return availabilityService.getSlots(courtId, date);
    }

    @PostMapping("/api/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateBookingRequest req) {
                                    
        return bookingService.create(userId(jwt), req);
    }

    @GetMapping("/api/bookings/me")
    public PageResponse<BookingResponse> myBookings(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.myBookings(userId(jwt), pageable);
    }

    @PatchMapping("/api/bookings/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookingService.cancel(userId(jwt), id);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}