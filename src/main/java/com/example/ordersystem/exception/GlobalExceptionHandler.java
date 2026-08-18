package com.example.ordersystem.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({ProductNotFoundException.class, OrderNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleConflict(InsufficientStockException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(field -> fields.put(field.getField(), field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Request validation failed", request, fields);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message,
            HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), fields));
    }
}
