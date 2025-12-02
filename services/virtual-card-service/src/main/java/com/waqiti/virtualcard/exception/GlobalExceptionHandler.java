package com.waqiti.virtualcard.exception;

import com.waqiti.virtualcard.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global Exception Handler for Virtual Card Service
 *
 * Provides centralized exception handling with:
 * - Structured error responses
 * - Request correlation IDs
 * - Security-safe error messages
 * - Comprehensive logging
 * - HTTP status code mapping
 * - Validation error details
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation exceptions from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        String errorId = generateErrorId();
        log.warn("Validation error [{}]: {} field errors - path: {}",
            errorId, fieldErrors.size(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Request validation failed. Please check your input.")
            .path(request.getRequestURI())
            .fieldErrors(fieldErrors)
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle type mismatch exceptions
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Type mismatch error [{}]: parameter '{}' - path: {}",
            errorId, ex.getName(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Parameter")
            .message(String.format("Parameter '%s' has invalid type", ex.getName()))
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Authentication error [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Authentication Failed")
            .message("Authentication is required to access this resource")
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handle authorization exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Authorization error [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Access Denied")
            .message("You do not have permission to access this resource")
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle security exceptions (generic)
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.error("Security error [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Security Error")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle card not found exceptions
     */
    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCardNotFoundException(
            CardNotFoundException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Card not found [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Card Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle card creation exceptions
     */
    @ExceptionHandler(CardCreationException.class)
    public ResponseEntity<ErrorResponse> handleCardCreationException(
            CardCreationException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.error("Card creation error [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Card Creation Failed")
            .message("Unable to create card. Please try again later.")
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle card secrets retrieval exceptions
     */
    @ExceptionHandler(CardSecretsRetrievalException.class)
    public ResponseEntity<ErrorResponse> handleCardSecretsRetrievalException(
            CardSecretsRetrievalException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.error("Card secrets retrieval error [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("Service Unavailable")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    /**
     * Handle insufficient funds exceptions
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Insufficient funds [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Insufficient Funds")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle card limit exceeded exceptions
     */
    @ExceptionHandler(CardLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleCardLimitExceededException(
            CardLimitExceededException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Card limit exceeded [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Card Limit Exceeded")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.warn("Illegal argument [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI());

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Request")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle illegal state exceptions
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.error("Illegal state [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Invalid State")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle generic exceptions (catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String errorId = generateErrorId();
        log.error("Unhandled exception [{}]: {} - path: {}",
            errorId, ex.getMessage(), request.getRequestURI(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorId(errorId)
            .timestamp(Instant.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred. Please contact support if the problem persists.")
            .path(request.getRequestURI())
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Generate unique error ID for tracking
     */
    private String generateErrorId() {
        return UUID.randomUUID().toString();
    }
}
