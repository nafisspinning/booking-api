package com.example.booking.common.exception;

import lombok.Getter;

/* fungsi untuk menangani exception bisnis. contoh: data tidak valid */
@Getter
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
    
}
