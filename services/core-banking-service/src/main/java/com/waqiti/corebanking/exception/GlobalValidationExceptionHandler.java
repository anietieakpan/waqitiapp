package com.waqiti.corebanking.exception;

import com.waqiti.corebanking.dto.ErrorResponseDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Validation Exception Handler
 *
 * Provides centralized exception handling for validation errors across the application.
 * Converts validation failures into clean, consistent error responses.
 *
 * Handles:
 * - @Valid annotation violations (MethodArgumentNotValidException)
 * - @Validated annotation violations (ConstraintViolationException)
 * - Custom validation exceptions
 *
 * Error Response Format:
 * - 400 Bad Request for validation errors
 * - Detailed field-level error messages
 * - Timestamp and error tracking
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@RestControllerAdvice
@Slf4j
public class GlobalValidationExceptionHandler {

    /**
     * Handle @Valid annotation violations on request body DTOs
     *
     * Triggered when:
     * - Controller method has @Valid parameter
     * - Request body fails validation constraints
     *
     * Example:
     * public ResponseEntity<?> createAccount(@Valid @RequestBody AccountCreationRequestDto request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        String errorSummary = String.format("Validation failed for %d field(s)", errors.size());
        log.warn("Validation error: {}", errorSummary);
        log.debug("Validation details: {}", errors);

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message(errorSummary)
            .details(errors)
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle @Validated annotation violations on method parameters
     *
     * Triggered when:
     * - Service/Controller class has @Validated annotation
     * - Method parameter fails validation constraints
     *
     * Example:
     * @Validated
     * public class AccountService {
     *     public void updateBalance(@NotNull UUID accountId, @TransactionAmount BigDecimal amount)
     * }
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> extractPropertyName(violation),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing // Keep first error if duplicate
            ));

        String errorSummary = String.format("Constraint violation on %d parameter(s)", errors.size());
        log.warn("Constraint violation: {}", errorSummary);
        log.debug("Constraint violation details: {}", errors);

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Constraint Violation")
            .message(errorSummary)
            .details(errors)
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle generic IllegalArgumentException for validation-like errors
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Argument")
            .message(ex.getMessage())
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Extract property name from constraint violation path
     *
     * Converts "processTransfer.arg0.amount" to "amount"
     */
    private String extractPropertyName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String[] parts = path.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : path;
    }
}
