package com.example.booking.booking;

import java.util.Set;

public enum BookingStatus {
    PENDING_PAYMENT, 
    CONFIRMED, 
    COMPLETED, 
    CANCELLED, 
    EXPIRED;

    /** Status yang "memegang" slot, sehingga slotnya tidak bisa dipesan orang lain. */
    public static final Set<BookingStatus> ACTIVE = Set.of(PENDING_PAYMENT, CONFIRMED);
}