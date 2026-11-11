package com.example.booking.common.exception;


/* fungsi untuk menangani exception ketika terjadi konflik data. contoh: data sudah ada */

public class ConflictException extends BusinessException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}