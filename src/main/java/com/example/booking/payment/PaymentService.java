package com.example.booking.payment;

import com.example.booking.booking.Booking;
import com.example.booking.booking.BookingService;
import com.example.booking.booking.BookingStatus;
import com.example.booking.common.exception.BusinessException;
import com.example.booking.common.exception.NotFoundException;
import com.example.booking.common.exception.UnAuthorizedException;
import com.example.booking.payment.dto.PaymentResponse;
import com.example.booking.payment.midtrans.MidtransClient;
import com.example.booking.payment.midtrans.MidtransDtos.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final MidtransClient midtransClient;
    private final Clock clock;

    @Transactional
    public PaymentResponse createPayment(UUID userId, UUID bookingId) {
        Booking booking = bookingService.findOwned(userId, bookingId);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new BusinessException("NOT_PAYABLE", "Booking tidak dalam status menunggu pembayaran");
        }
        if (booking.getExpiresAt().isBefore(clock.instant())) {
            // Tidak memanggil booking.expire() di sini: exception me-rollback transaksi,
            // jadi perubahannya akan hilang. Status EXPIRED diurus BookingExpiryJob.
            throw new BusinessException("BOOKING_EXPIRED", "Batas waktu pembayaran sudah lewat");
        }

        // Idempoten: klik "Bayar" dua kali tidak membuat dua transaksi
        var existing = paymentRepository.findByBookingId(bookingId);
        if (existing.isPresent()) {
            return PaymentResponse.from(existing.get());
        }

        var request = new SnapRequest(
                new TransactionDetails(booking.getCode(), booking.getTotalPrice().longValueExact()),
                new CustomerDetails(booking.getUser().getFullName(), booking.getUser().getEmail()),
                new Expiry("minutes", 15));
        SnapResponse snap = midtransClient.createTransaction(request);

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setSnapToken(snap.token());
        payment.setRedirectUrl(snap.redirectUrl());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional
    public void handleNotification(Notification n) {
        if (!midtransClient.isValidSignature(n)) {
            log.warn("Invalid Midtrans signature for order {}", n.orderId());
            throw new UnAuthorizedException("Signature tidak valid");
        }

        Payment payment = paymentRepository.findByBookingCode(n.orderId())
                .orElseThrow(() -> new NotFoundException("Order tidak dikenal"));
        Booking booking = payment.getBooking();

        if (new BigDecimal(n.grossAmount()).compareTo(payment.getAmount()) != 0) {
            log.error("Amount mismatch for order {}: {}", n.orderId(), n.grossAmount());
            throw new BusinessException("AMOUNT_MISMATCH", "Nominal tidak sesuai");
        }

        // Idempoten: Midtrans bisa mengirim notifikasi yang sama berkali-kali
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Order {} already processed ({}), skipping", n.orderId(), payment.getStatus());
            return;
        }

        switch (n.transactionStatus()) {
            case "settlement", "capture" -> {
                if ("capture".equals(n.transactionStatus()) && !"accept".equals(n.fraudStatus())) {
                    return; // kartu kredit masih dicek fraud, tunggu notifikasi berikutnya
                }
                payment.markPaid(n.transactionId(), clock.instant());
                if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                    booking.confirm();
                } else {
                    // Dibayar setelah booking expired: uang masuk tapi slot sudah lepas
                    log.error("Order {} paid but booking is {}. Manual refund needed",
                            n.orderId(), booking.getStatus());
                }
            }
            case "expire", "cancel", "deny" -> {
                payment.markFailed();
                if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                    booking.expire();
                }
            }
            default -> log.info("Order {} status {}, no action", n.orderId(), n.transactionStatus());
        }
    }
}