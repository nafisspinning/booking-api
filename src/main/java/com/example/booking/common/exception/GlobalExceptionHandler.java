package com.example.booking.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // menangani exception NotFoundException, ConflictException, BusinessException, MethodArgumentNotValidException, dan Exception umum
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    // menangani exception ConflictException, yang merupakan subclass dari BusinessException
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    // menangani exception BusinessException, yang merupakan superclass dari ConflictException
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        // 422 Unprocessable Content: request valid secara format, tapi melanggar aturan bisnis
        return problem(HttpStatusCode.valueOf(422), ex.getCode(), ex.getMessage());
    }

    // menangani exception MethodArgumentNotValidException, yang terjadi ketika validasi input gagal
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Input tidak valid");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }


    // menangani exception umum yang tidak ditangani oleh handler lainnya
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Terjadi kesalahan pada server");
    }

    // membuat objek ProblemDetail untuk response error
    private ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}