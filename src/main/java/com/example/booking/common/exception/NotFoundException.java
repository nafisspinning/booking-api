package com.example.booking.common.exception;

/* fungsi untuk menangani exception ketika data tidak ditemukan */

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}


