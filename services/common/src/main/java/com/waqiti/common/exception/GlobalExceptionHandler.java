// File: services/common/src/main/java/com/waqiti/common/exception/GlobalExceptionHandler.java
package com.waqiti.common.exception;

import com.waqiti.common.error.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler that can be extended by all services
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Financial-specific exception handlers
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex, WebRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Insufficient funds - Error ID: {} - {}", errorId, ex.getMessage(), ex);
        return buildFinancialErrorResponse(HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS", 
                ex.getMessage(), request, errorId);
    }

    @ExceptionHandler(TransactionFailedException.class)
    public ResponseEntity<ErrorResponse> handleTransactionFailed(TransactionFailedException ex, WebRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Transaction failed - Error ID: {} - {}", errorId, ex.getMessage(), ex);
        return buildFinancialErrorResponse(HttpStatus.BAD_REQUEST, "TRANSACTION_FAILED", 
                ex.getMessage(), request, errorId);
    }

    @ExceptionHandler(InvalidResourceStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResourceState(InvalidResourceStateException ex, WebRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Invalid resource state - Error ID: {} - {}", errorId, ex.getMessage(), ex);
        return buildFinancialErrorResponse(HttpStatus.CONFLICT, "INVALID_STATE", 
                ex.getMessage(), request, errorId);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error("Resource not found - Error ID: {} - {}", errorId, ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, errorId);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex, WebRequest request) {
        log.error("Duplicate resource", ex);
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        log.error("Business exception", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.error("Access denied", ex);
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.error("Authentication failed", ex);
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validation error", ex);

        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing + ", " + replacement
                ));

        // Use the builder pattern with ValidationErrorResponse
        ValidationErrorResponse response = ValidationErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Invalid input. Please check the submitted fields.")
                .validationErrors(errors)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        log.error("Constraint violation", ex);

        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (existing, replacement) -> existing + ", " + replacement
                ));

        // Use the builder pattern with ValidationErrorResponse
        ValidationErrorResponse response = ValidationErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Invalid input. Please check the submitted fields.")
                .validationErrors(errors)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.error("Illegal argument", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, WebRequest request) {
        log.error("Illegal state", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.error("Method not supported", ex);
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, 
            "HTTP method not supported for this endpoint", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, WebRequest request) {
        log.error("Media type not supported", ex);
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 
            "Media type not supported", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.error("Message not readable", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, 
            "Invalid JSON format or malformed request body", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, WebRequest request) {
        log.error("Missing request parameter", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, 
            "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {
        log.error("Type mismatch", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, 
            "Invalid parameter type for '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {
        log.error("Data integrity violation", ex);
        return buildErrorResponse(HttpStatus.CONFLICT, 
            "Data integrity constraint violation", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, WebRequest request) {
        log.error("Optimistic locking failure", ex);
        return buildErrorResponse(HttpStatus.CONFLICT, 
            "Resource was modified by another user. Please refresh and try again", request);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystemException(
            TransactionSystemException ex, WebRequest request) {
        log.error("Transaction system exception", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
            "Transaction failed. Please try again", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {
        log.error("File upload size exceeded", ex);
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, 
            "File size exceeds maximum allowed limit", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, WebRequest request) {
        log.error("Runtime exception", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
            "An unexpected runtime error occurred", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request
        );
    }

    protected ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message,
                                                           WebRequest request, String errorId) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getDescription(false).substring(4))
                .errorId(errorId)
                .build();

        return new ResponseEntity<>(response, status);
    }

    protected ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, WebRequest request) {
        return buildErrorResponse(status, message, request, UUID.randomUUID().toString());
    }

    protected ResponseEntity<ErrorResponse> buildFinancialErrorResponse(HttpStatus status, String errorCode,
                                                                      String message, WebRequest request, String errorId) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(status.value())
                .error(errorCode)
                .message(message)
                .path(request.getDescription(false).substring(4))
                .errorId(errorId)
                .build();

        return new ResponseEntity<>(response, status);
    }
}