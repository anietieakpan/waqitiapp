package com.waqiti.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.concurrent.TimeoutException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Standardized error handler for all Waqiti services
 * Provides consistent error responses across the platform
 */
@RestControllerAdvice
@Slf4j
public class StandardizedErrorHandler {
    
    private static final String DEFAULT_ERROR_MESSAGE = "An unexpected error occurred";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    // ========== Business Logic Exceptions ==========
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Insufficient funds - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "INSUFFICIENT_FUNDS",
            "The wallet does not have sufficient funds for this transaction",
            request.getRequestURI(),
            correlationId,
            Map.of(
                "walletId", ex.getWalletId(),
                "requestedAmount", ex.getRequestedAmount(),
                "availableAmount", ex.getAvailableAmount()
            )
        );
    }
    
    @ExceptionHandler(TransactionFailedException.class)
    public ResponseEntity<ErrorResponse> handleTransactionFailed(
            TransactionFailedException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Transaction failed - Correlation ID: {} - {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "TRANSACTION_FAILED",
            ex.getMessage(),
            request.getRequestURI(),
            correlationId,
            Map.of(
                "transactionId", ex.getTransactionId(),
                "failureReason", ex.getFailureReason()
            )
        );
    }
    
    @ExceptionHandler(PaymentLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePaymentLimitExceeded(
            PaymentLimitExceededException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Payment limit exceeded - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_LIMIT_EXCEEDED",
            "The payment amount exceeds your allowed limits",
            request.getRequestURI(),
            correlationId,
            Map.of(
                "limitType", ex.getLimitType(),
                "requestedAmount", ex.getRequestedAmount(),
                "limitAmount", ex.getLimitAmount()
            )
        );
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Resource not found - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            ex.getMessage(),
            request.getRequestURI(),
            correlationId,
            Map.of("resourceType", ex.getResourceType(), "resourceId", ex.getResourceId())
        );
    }
    
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Duplicate resource - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.CONFLICT,
            "DUPLICATE_RESOURCE",
            ex.getMessage(),
            request.getRequestURI(),
            correlationId,
            Map.of("resourceType", ex.getResourceType(), "conflictingField", ex.getConflictingField())
        );
    }
    
    @ExceptionHandler(InvalidResourceStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResourceState(
            InvalidResourceStateException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Invalid resource state - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.CONFLICT,
            "INVALID_RESOURCE_STATE",
            ex.getMessage(),
            request.getRequestURI(),
            correlationId,
            Map.of(
                "resourceType", ex.getResourceType(),
                "currentState", ex.getCurrentState(),
                "expectedState", ex.getExpectedState()
            )
        );
    }
    
    // ========== Security Exceptions ==========
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Authentication failed - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "AUTHENTICATION_FAILED",
            "Authentication credentials are missing or invalid",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Bad credentials - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "The provided credentials are invalid",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Access denied - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "You do not have permission to perform this action",
            request.getRequestURI(),
            correlationId
        );
    }
    
    // ========== Validation Exceptions ==========
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Validation error - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                (existing, replacement) -> existing + "; " + replacement
            ));
        
        return ResponseEntity.badRequest().body(ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("VALIDATION_FAILED")
            .message("Request validation failed")
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .validationErrors(fieldErrors)
            .build());
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Constraint violation - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        Map<String, String> violations = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing + "; " + replacement
            ));
        
        return ResponseEntity.badRequest().body(ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("CONSTRAINT_VIOLATION")
            .message("Constraint validation failed")
            .path(request.getRequestURI())
            .correlationId(correlationId)
            .validationErrors(violations)
            .build());
    }
    
    // ========== HTTP/Web Exceptions ==========
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Method not supported - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()),
            request.getRequestURI(),
            correlationId,
            Map.of("supportedMethods", ex.getSupportedHttpMethods())
        );
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Media type not supported - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "The media type is not supported",
            request.getRequestURI(),
            correlationId,
            Map.of("supportedMediaTypes", ex.getSupportedMediaTypes())
        );
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Message not readable - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        String message = "Invalid JSON format or malformed request body";
        String errorCode = "INVALID_REQUEST_BODY";
        
        // Provide more specific error messages for common JSON errors
        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) ex.getCause();
            message = String.format("Invalid value '%s' for field '%s'", 
                ife.getValue(), ife.getPath().get(0).getFieldName());
            errorCode = "INVALID_FIELD_FORMAT";
        } else if (ex.getCause() instanceof MismatchedInputException) {
            message = "Required field is missing or has wrong type";
            errorCode = "MISSING_REQUIRED_FIELD";
        }
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            errorCode,
            message,
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Missing request parameter - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "MISSING_PARAMETER",
            String.format("Required parameter '%s' is missing", ex.getParameterName()),
            request.getRequestURI(),
            correlationId,
            Map.of("parameterName", ex.getParameterName(), "parameterType", ex.getParameterType())
        );
    }
    
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Missing request header - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "MISSING_HEADER",
            String.format("Required header '%s' is missing", ex.getHeaderName()),
            request.getRequestURI(),
            correlationId,
            Map.of("headerName", ex.getHeaderName())
        );
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Type mismatch - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER_TYPE",
            String.format("Invalid type for parameter '%s'. Expected %s", 
                ex.getName(), ex.getRequiredType().getSimpleName()),
            request.getRequestURI(),
            correlationId,
            Map.of(
                "parameterName", ex.getName(),
                "providedValue", ex.getValue(),
                "expectedType", ex.getRequiredType().getSimpleName()
            )
        );
    }
    
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("No handler found - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.NOT_FOUND,
            "ENDPOINT_NOT_FOUND",
            String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
            request.getRequestURI(),
            correlationId
        );
    }
    
    // ========== Resilience4j Exceptions ==========
    
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerOpen(
            CallNotPermittedException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Circuit breaker open - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SERVICE_UNAVAILABLE",
            "The service is temporarily unavailable. Please try again later",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RequestNotPermitted ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Rate limit exceeded - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.TOO_MANY_REQUESTS,
            "RATE_LIMIT_EXCEEDED",
            "Too many requests. Please slow down and try again later",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler({TimeoutException.class})
    public ResponseEntity<ErrorResponse> handleTimeout(
            Exception ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Request timeout - Correlation ID: {} - {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.REQUEST_TIMEOUT,
            "REQUEST_TIMEOUT",
            "The request took too long to process",
            request.getRequestURI(),
            correlationId
        );
    }
    
    // ========== Database Exceptions ==========
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Data integrity violation - Correlation ID: {} - {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.CONFLICT,
            "DATA_INTEGRITY_VIOLATION",
            "The operation violates data integrity constraints",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Optimistic locking failure - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.CONFLICT,
            "CONCURRENT_MODIFICATION",
            "The resource was modified by another user. Please refresh and try again",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(
            DataAccessException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Database error - Correlation ID: {} - {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "DATABASE_ERROR",
            "A database error occurred while processing your request",
            request.getRequestURI(),
            correlationId
        );
    }
    
    // ========== File Upload Exceptions ==========
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("File upload size exceeded - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "FILE_TOO_LARGE",
            String.format("File size exceeds maximum allowed limit of %d bytes", ex.getMaxUploadSize()),
            request.getRequestURI(),
            correlationId,
            Map.of("maxUploadSize", ex.getMaxUploadSize())
        );
    }
    
    // ========== Generic Exceptions ==========
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.warn("Illegal argument - Correlation ID: {} - {}", correlationId, ex.getMessage());
        
        return createErrorResponse(
            HttpStatus.BAD_REQUEST,
            "INVALID_ARGUMENT",
            ex.getMessage(),
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Illegal state - Correlation ID: {} - {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INVALID_STATE",
            "The application is in an invalid state to process this request",
            request.getRequestURI(),
            correlationId
        );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        String correlationId = getCorrelationId(request);
        log.error("Unexpected error - Correlation ID: {} - {}", correlationId, ex.getMessage(), ex);
        
        return createErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            DEFAULT_ERROR_MESSAGE,
            request.getRequestURI(),
            correlationId
        );
    }
    
    // ========== Helper Methods ==========
    
    private ResponseEntity<ErrorResponse> createErrorResponse(
            HttpStatus status, String errorCode, String message, 
            String path, String correlationId) {
        return createErrorResponse(status, errorCode, message, path, correlationId, null);
    }
    
    private ResponseEntity<ErrorResponse> createErrorResponse(
            HttpStatus status, String errorCode, String message, 
            String path, String correlationId, Map<String, Object> details) {
        
        ErrorResponse response = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status.value())
            .error(errorCode)
            .message(message)
            .path(path)
            .correlationId(correlationId)
            .details(details)
            .build();
        
        return ResponseEntity.status(status)
            .header(CORRELATION_ID_HEADER, correlationId)
            .body(response);
    }
    
    private String getCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}