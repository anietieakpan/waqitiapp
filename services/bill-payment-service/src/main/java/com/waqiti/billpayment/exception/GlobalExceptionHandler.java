package com.waqiti.billpayment.exception;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global exception handler for bill payment service
 * Provides consistent error responses across all endpoints
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handle bill not found exceptions
     */
    @ExceptionHandler(BillNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBillNotFound(
            BillNotFoundException ex, HttpServletRequest request) {
        log.warn("Bill not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle payment not found exceptions
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(
            PaymentNotFoundException ex, HttpServletRequest request) {
        log.warn("Payment not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle biller not found exceptions
     */
    @ExceptionHandler(BillerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBillerNotFound(
            BillerNotFoundException ex, HttpServletRequest request) {
        log.warn("Biller not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle auto-pay config not found exceptions
     */
    @ExceptionHandler(AutoPayConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAutoPayConfigNotFound(
            AutoPayConfigNotFoundException ex, HttpServletRequest request) {
        log.warn("Auto-pay config not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.NOT_FOUND.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle invalid payment state exceptions
     */
    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentState(
            InvalidPaymentStateException ex, HttpServletRequest request) {
        log.warn("Invalid payment state: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .details("The requested operation cannot be performed in the current payment state")
                .status(HttpStatus.CONFLICT.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle insufficient balance exceptions
     */
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(
            InsufficientBalanceException ex, HttpServletRequest request) {
        log.warn("Insufficient balance: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .details("Please add funds to your wallet to complete this payment")
                .status(HttpStatus.PAYMENT_REQUIRED.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(error);
    }

    /**
     * Handle payment limit exceeded exceptions
     */
    @ExceptionHandler(PaymentLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePaymentLimitExceeded(
            PaymentLimitExceededException ex, HttpServletRequest request) {
        log.warn("Payment limit exceeded: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle duplicate payment exceptions
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePayment(
            DuplicatePaymentException ex, HttpServletRequest request) {
        log.warn("Duplicate payment detected: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .details("A payment with the same idempotency key already exists")
                .status(HttpStatus.CONFLICT.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle wallet service exceptions
     */
    @ExceptionHandler(WalletServiceException.class)
    public ResponseEntity<ErrorResponse> handleWalletServiceException(
            WalletServiceException ex, HttpServletRequest request) {
        log.error("Wallet service error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message("Wallet service temporarily unavailable")
                .details(ex.getMessage())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Handle biller integration exceptions
     */
    @ExceptionHandler(BillerIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleBillerIntegrationException(
            BillerIntegrationException ex, HttpServletRequest request) {
        log.error("Biller integration error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message("Biller service temporarily unavailable")
                .details(ex.getMessage())
                .status(HttpStatus.BAD_GATEWAY.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    /**
     * Handle bill sharing exceptions
     */
    @ExceptionHandler(BillSharingException.class)
    public ResponseEntity<ErrorResponse> handleBillSharingException(
            BillSharingException ex, HttpServletRequest request) {
        log.warn("Bill sharing error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Handle payment processing exceptions
     */
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessingException(
            PaymentProcessingException ex, HttpServletRequest request) {
        log.error("Payment processing error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .rejectedValue(error.getRejectedValue() != null ? error.getRejectedValue().toString() : null)
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message("Validation failed for request")
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ErrorResponse.FieldError.builder()
                        .field(violation.getPropertyPath().toString())
                        .rejectedValue(violation.getInvalidValue() != null ? violation.getInvalidValue().toString() : null)
                        .message(violation.getMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("CONSTRAINT_VIOLATION")
                .message("Constraint violation")
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INVALID_ARGUMENT")
                .message(ex.getMessage())
                .status(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle illegal state exceptions
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Illegal state: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INVALID_STATE")
                .message(ex.getMessage())
                .status(HttpStatus.CONFLICT.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("AUTHENTICATION_FAILED")
                .message("Authentication failed")
                .details(ex.getMessage())
                .status(HttpStatus.UNAUTHORIZED.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("Access denied")
                .details("You don't have permission to access this resource")
                .status(HttpStatus.FORBIDDEN.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle Feign client exceptions
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(
            FeignException ex, HttpServletRequest request) {
        log.error("Feign client error: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("EXTERNAL_SERVICE_ERROR")
                .message("External service temporarily unavailable")
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Handle data integrity violation exceptions
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("DATA_INTEGRITY_VIOLATION")
                .message("Data integrity violation")
                .details("The operation violates data integrity constraints")
                .status(HttpStatus.CONFLICT.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred")
                .details("Please contact support if the problem persists")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
