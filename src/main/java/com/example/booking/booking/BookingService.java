package com.example.booking.booking;

import com.example.booking.auth.UserRepository;
import com.example.booking.booking.dto.BookingResponse;
import com.example.booking.booking.dto.CreateBookingRequest;
import com.example.booking.common.exception.BusinessException;
import com.example.booking.common.exception.ConflictException;
import com.example.booking.common.exception.NotFoundException;
import com.example.booking.common.web.PageResponse;
import com.example.booking.venue.Court;
import com.example.booking.venue.CourtRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(15);
    static final Duration CANCEL_CUTOFF = Duration.ofHours(24);
    static final int MAX_HOURS = 4;
    static final int MAX_DAYS_AHEAD = 30;
    static final String OVERLAP_CONSTRAINT = "no_overlapping_active_booking";
    static final String EXCLUSION_VIOLATION = "23P01"; // SQLState PostgreSQL untuk pelanggaran EXCLUDE

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public BookingResponse create(UUID userId, CreateBookingRequest req) {
        Court court = courtRepository
                        .findWithVenueById(req.courtId())
                        .orElseThrow(() -> new NotFoundException("Lapangan tidak ditemukan"));

        validate(court, req);                                    // R1–R5

        if (bookingRepository.existsOverlap(court.getId(), req.date(),
                req.startTime(), req.endTime(), BookingStatus.ACTIVE)) {   // R6
            throw new ConflictException("SLOT_TAKEN",
                    "Slot %s-%s sudah dipesan".formatted(req.startTime(), req.endTime()));
        }
        // try { Thread.sleep(300); } catch (InterruptedException ignored) { }
        

        long hours = Duration.between(req.startTime(), req.endTime()).toHours();

        Booking booking = new Booking();
        booking.setCode(generateCode(req.date()));
        booking.setUser(userRepository.getReferenceById(userId));
        booking.setCourt(court);
        booking.setBookingDate(req.date());
        booking.setStartTime(req.startTime());
        booking.setEndTime(req.endTime());
        booking.setTotalPrice(court.getPricePerHour().multiply(BigDecimal.valueOf(hours)));
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setExpiresAt(clock.instant().plus(PAYMENT_WINDOW));

        // existsOverlap di atas bisa lolos bersamaan untuk dua request; constraint DB yang jadi penjaga akhir.
        // saveAndFlush memaksa INSERT terjadi di sini (bukan saat commit) agar pelanggarannya bisa ditangkap.
        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new ConflictException("SLOT_TAKEN",
                        "Slot %s-%s baru saja dipesan orang lain".formatted(req.startTime(), req.endTime()));
            }
            throw ex;
        }
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookings(UUID userId, Pageable pageable) {
        return PageResponse.from(bookingRepository.findByUserId(userId, pageable)
                .map(BookingResponse::from));
    }

    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        Booking booking = findOwned(userId, bookingId);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {    // R7
            LocalDateTime cutoff = booking.startDateTime().minus(CANCEL_CUTOFF);
            if (LocalDateTime.now(clock).isAfter(cutoff)) {
                throw new BusinessException("CANCEL_TOO_LATE",
                        "Pembatalan maksimal 24 jam sebelum jadwal main");
            }
        }
        booking.cancel();
        return BookingResponse.from(booking);
    }

    public Booking findOwned(UUID userId, UUID bookingId) {
        return bookingRepository.findWithCourtById(bookingId)
                .filter(b -> b.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Booking tidak ditemukan"));
    }

    // hanya pelanggaran constraint overlap (V2__prevent_overlapping_bookings.sql) yang berarti slot bentrok.
    // Dicek dari SQLException asli karena Hibernate tidak mengisi getConstraintName() untuk SQLState 23P01 (null).
    private boolean isOverlapViolation(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return EXCLUSION_VIOLATION.equals(sql.getSQLState())
                        && sql.getMessage() != null
                        && sql.getMessage().contains(OVERLAP_CONSTRAINT);
            }
        }
        return false;
    }


    private void validate(Court court, CreateBookingRequest req) {
        LocalTime start = req.startTime();
        LocalTime end = req.endTime();

        if (start.getMinute() != 0 || end.getMinute() != 0
                || start.getSecond() != 0 || end.getSecond() != 0) {
            throw new BusinessException("INVALID_SLOT", "Jam harus bulat, contoh 19:00");
        }
        long hours = Duration.between(start, end).toHours();
        if (!start.isBefore(end) || hours < 1 || hours > MAX_HOURS) {
            throw new BusinessException("INVALID_DURATION",
                    "Durasi booking 1 sampai %d jam".formatted(MAX_HOURS));
        }
        if (!court.getVenue().isOpenBetween(start, end)) {
            throw new BusinessException("OUTSIDE_OPERATING_HOURS", "Di luar jam operasional venue");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!LocalDateTime.of(req.date(), start).isAfter(now)
                || req.date().isAfter(now.toLocalDate().plusDays(MAX_DAYS_AHEAD))) {
            throw new BusinessException("INVALID_DATE",
                    "Tanggal harus di masa depan, maksimal %d hari lagi".formatted(MAX_DAYS_AHEAD));
        }
        if (!court.isActive()) {
            throw new BusinessException("COURT_INACTIVE", "Lapangan sedang tidak tersedia");
        }
    }

    private String generateCode(LocalDate date) {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return "BK-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
    }
}