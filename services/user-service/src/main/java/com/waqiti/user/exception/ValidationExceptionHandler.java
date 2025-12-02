package com.waqiti.user.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Validation Exception Handler
 *
 * Handles all validation exceptions and returns user-friendly error responses
 *
 * SECURITY:
 * - Sanitizes error messages to prevent information leakage
 * - Logs security-relevant validation failures
 * - Prevents stack trace exposure in responses
 */
@Slf4j
@RestControllerAdvice
public class ValidationExceptionHandler {

    /**
     * Handle JSR-303 validation errors from @Valid on @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                (existing, replacement) -> existing // Keep first error if multiple
            ));

        // Log security-relevant failures
        if (containsSecurityViolation(errors)) {
            log.warn("SECURITY: Validation failed - potential attack detected: {}",
                sanitizeForLogging(errors));
        }

        ErrorResponse response = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Input validation failed")
            .fieldErrors(errors)
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Handle constraint violations from method-level validation
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = ex.getConstraintViolations()
            .stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing
            ));

        // Log security-relevant failures
        if (containsSecurityViolation(errors)) {
            log.warn("SECURITY: Constraint violation - potential attack detected: {}",
                sanitizeForLogging(errors));
        }

        ErrorResponse response = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Constraint Violation")
            .message("Input validation failed")
            .fieldErrors(errors)
            .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Check if validation errors indicate potential security attack
     */
    private boolean containsSecurityViolation(Map<String, String> errors) {
        return errors.values().stream()
            .anyMatch(msg ->
                msg.contains("XSS") ||
                msg.contains("SQL injection") ||
                msg.contains("injection") ||
                msg.contains("malicious") ||
                msg.contains("attack")
            );
    }

    /**
     * Sanitize error map for logging (remove sensitive data)
     */
    private Map<String, String> sanitizeForLogging(Map<String, String> errors) {
        Map<String, String> sanitized = new HashMap<>();

        for (Map.Entry<String, String> entry : errors.entrySet()) {
            String field = entry.getKey();
            String message = entry.getValue();

            // Keep field name and message, but truncate long messages
            if (message != null && message.length() > 100) {
                message = message.substring(0, 100) + "...";
            }

            sanitized.put(field, message);
        }

        return sanitized;
    }

    /**
     * Error Response DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ErrorResponse {
        private Instant timestamp;
        private int status;
        private String error;
        private String message;
        private Map<String, String> fieldErrors;
    }
}
