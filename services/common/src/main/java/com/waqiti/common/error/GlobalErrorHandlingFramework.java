package com.waqiti.common.error;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.exception.*;
import com.waqiti.common.domain.exceptions.PaymentDomainExceptions.PaymentProcessingException;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Comprehensive Global Error Handling Framework
 * Provides centralized error handling for all microservices with:
 * - Consistent error responses
 * - Detailed error tracking
 * - Security considerations
 * - Monitoring integration
 * - Automatic alerting for critical errors
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorHandlingFramework extends ResponseEntityExceptionHandler {

    private final AuditService auditService;
    private final MetricsService metricsService;
    private final AlertService alertService;
    
    @Value("${app.error.include-stacktrace:false}")
    private boolean includeStackTrace;
    
    @Value("${app.error.include-details:false}")
    private boolean includeErrorDetails;
    
    @Value("${app.environment:production}")
    private String environment;

    // Business Logic Exceptions
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Business Rule Violation")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode(ex.getErrorCode())
            .build();
        
        logError(ex, request, ErrorSeverity.MEDIUM);
        recordMetrics("business_exception", ex.getErrorCode());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error("Resource Not Found")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("RESOURCE_NOT_FOUND")
            .build();
        
        logError(ex, request, ErrorSeverity.LOW);
        recordMetrics("resource_not_found", ex.getResourceType());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Duplicate Resource")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("DUPLICATE_RESOURCE")
            .duplicateField(ex.getField())
            .build();
        
        logError(ex, request, ErrorSeverity.LOW);
        recordMetrics("duplicate_resource", ex.getResourceType());
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    // Validation Exceptions
    
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        
        Map<String, List<String>> validationErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.groupingBy(
                FieldError::getField,
                Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
            ));
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Request validation failed")
            .path(getPath(request))
            .traceId(getTraceId())
            .errorCode("VALIDATION_ERROR")
            .validationErrors(validationErrors)
            .build();
        
        logValidationError(validationErrors, request);
        recordMetrics("validation_error", "method_argument");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        Map<String, List<String>> validationErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            validationErrors.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(message);
        }
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Constraint Violation")
            .message("Validation constraints violated")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("CONSTRAINT_VIOLATION")
            .validationErrors(validationErrors)
            .build();
        
        logValidationError(validationErrors, request);
        recordMetrics("validation_error", "constraint_violation");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    // Security Exceptions
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Access Denied")
            .message("You do not have permission to access this resource")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("ACCESS_DENIED")
            .build();
        
        logSecurityError(ex, request, "ACCESS_DENIED");
        recordMetrics("security_error", "access_denied");
        auditService.logSecurityViolation(request, "ACCESS_DENIED");
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        String message = "Authentication failed";
        String errorCode = "AUTH_FAILED";
        
        if (ex instanceof BadCredentialsException) {
            message = "Invalid credentials";
            errorCode = "INVALID_CREDENTIALS";
        }
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Authentication Failed")
            .message(message)
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode(errorCode)
            .build();
        
        logSecurityError(ex, request, errorCode);
        recordMetrics("security_error", "authentication_failed");
        auditService.logAuthenticationFailure(request);
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex, HttpServletRequest request) {
        
        // Don't expose security details
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Security Error")
            .message("A security error occurred")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("SECURITY_ERROR")
            .build();
        
        logSecurityError(ex, request, "SECURITY_VIOLATION");
        recordMetrics("security_error", "security_exception");
        auditService.logSecurityViolation(request, "SECURITY_EXCEPTION");
        Map<String, Object> alertContext = Map.of("errorType", ex.getClass().getName());
        alertService.sendSecurityAlert("Security exception", ex.getMessage(), alertContext);
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    
    // Database Exceptions
    
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(
            OptimisticLockException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error("Concurrent Modification")
            .message("The resource was modified by another user. Please refresh and try again.")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("OPTIMISTIC_LOCK")
            .build();
        
        logError(ex, request, ErrorSeverity.MEDIUM);
        recordMetrics("database_error", "optimistic_lock");
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(PessimisticLockException.class)
    public ResponseEntity<ErrorResponse> handlePessimisticLockException(
            PessimisticLockException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.LOCKED.value())
            .error("Resource Locked")
            .message("The resource is currently locked. Please try again later.")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("PESSIMISTIC_LOCK")
            .retryAfter(5) // seconds
            .build();
        
        logError(ex, request, ErrorSeverity.MEDIUM);
        recordMetrics("database_error", "pessimistic_lock");
        
        return ResponseEntity.status(HttpStatus.LOCKED).body(error);
    }
    
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorResponse> handleSQLException(
            SQLException ex, HttpServletRequest request) {
        
        // Don't expose SQL details in production
        String message = isProduction() ? 
            "A database error occurred" : 
            "Database error: " + ex.getMessage();
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Database Error")
            .message(message)
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("DATABASE_ERROR")
            .build();
        
        logError(ex, request, ErrorSeverity.HIGH);
        recordMetrics("database_error", "sql_exception");
        alertService.sendDatabaseAlert("SQL Exception", ex.getMessage(), 0);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    // Timeout Exceptions
    
    @ExceptionHandler({TimeoutException.class, SocketTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleTimeoutException(
            Exception ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.REQUEST_TIMEOUT.value())
            .error("Request Timeout")
            .message("The request took too long to process")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("TIMEOUT")
            .build();
        
        logError(ex, request, ErrorSeverity.MEDIUM);
        recordMetrics("timeout_error", ex.getClass().getSimpleName());
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(error);
    }
    
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAsyncRequestTimeoutException(
            AsyncRequestTimeoutException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("Service Timeout")
            .message("The service is currently unavailable. Please try again later.")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("ASYNC_TIMEOUT")
            .retryAfter(30) // seconds
            .build();
        
        logError(ex, request, ErrorSeverity.HIGH);
        recordMetrics("timeout_error", "async_request");
        alertService.sendPerformanceAlert("Async request timeout", getPath(request), ErrorSeverity.MEDIUM);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
    
    // Rate Limiting
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(
            RateLimitExceededException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error("Rate Limit Exceeded")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("RATE_LIMIT_EXCEEDED")
            .retryAfter((int) ex.getRetryAfter())
            .remainingLimit((int) ex.getRemainingLimit())
            .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Limit", String.valueOf(ex.getLimit()));
        headers.add("X-RateLimit-Remaining", String.valueOf(ex.getRemainingLimit()));
        headers.add("X-RateLimit-Reset", String.valueOf(ex.getResetTime()));
        headers.add("Retry-After", String.valueOf(ex.getRetryAfter()));
        
        logError(ex, request, ErrorSeverity.LOW);
        recordMetrics("rate_limit", request.getRemoteAddr());
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(error);
    }
    
    // File Upload Exceptions
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
            .error("File Too Large")
            .message("The uploaded file exceeds the maximum allowed size")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("FILE_TOO_LARGE")
            .maxSize(ex.getMaxUploadSize())
            .build();
        
        logError(ex, request, ErrorSeverity.LOW);
        recordMetrics("file_upload_error", "size_exceeded");
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }
    
    // External Service Exceptions
    
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceException(
            ExternalServiceException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_GATEWAY.value())
            .error("External Service Error")
            .message("An external service is unavailable")
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("EXTERNAL_SERVICE_ERROR")
            .service(ex.getServiceName())
            .retryAfter((int) ex.getRetryAfter())
            .build();
        
        logError(ex, request, ErrorSeverity.HIGH);
        recordMetrics("external_service_error", ex.getServiceName());
        alertService.sendIntegrationAlert(ex.getServiceName(), ex.getMessage(), ErrorSeverity.HIGH);
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }
    
    // Payment Specific Exceptions
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.PAYMENT_REQUIRED.value())
            .error("Insufficient Funds")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("INSUFFICIENT_FUNDS")
            .availableBalance(ex.getAvailableBalance())
            .requiredAmount(ex.getRequiredAmount())
            .build();
        
        logError(ex, request, ErrorSeverity.LOW);
        recordMetrics("payment_error", "insufficient_funds");
        auditService.logPaymentFailure(request, "INSUFFICIENT_FUNDS");
        
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(error);
    }
    
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessingException(
            PaymentProcessingException ex, HttpServletRequest request) {
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .error("Payment Processing Error")
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode(ex.getErrorCode())
            .paymentProvider(ex.getProvider())
            .build();
        
        logError(ex, request, ErrorSeverity.MEDIUM);
        recordMetrics("payment_error", ex.getErrorCode());
        auditService.logPaymentError(request, ex.getErrorCode());
        
        if (ex.isCritical()) {
            alertService.sendPaymentAlert(ex.getMessage(), ex.getProvider(), ErrorSeverity.HIGH);
        }
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }
    
    // Generic Exception Handler (Catch-all)
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        String errorId = UUID.randomUUID().toString();
        
        // Log full exception details
        log.error("Unhandled exception [ID: {}]: ", errorId, ex);
        
        // Don't expose internal details in production
        String message = isProduction() ? 
            "An unexpected error occurred. Error ID: " + errorId :
            ex.getMessage();
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message(message)
            .path(request.getRequestURI())
            .traceId(getTraceId())
            .errorCode("INTERNAL_ERROR")
            .errorId(errorId)
            .build();
        
        if (includeStackTrace && !isProduction()) {
            error.setStackTrace(getStackTraceAsString(ex));
        }
        
        recordMetrics("unhandled_exception", ex.getClass().getSimpleName());
        auditService.logSystemError(errorId, ex);
        
        // Send alert for unhandled exceptions
        alertService.sendSystemAlert(
            "Unhandled Exception", 
            String.format("Error ID: %s, Exception: %s", errorId, ex.getMessage()),
            ErrorSeverity.CRITICAL
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    // Helper Methods
    
    private void logError(Exception ex, HttpServletRequest request, ErrorSeverity severity) {
        String message = String.format(
            "Error processing request [%s %s] from [%s]: %s",
            request.getMethod(),
            request.getRequestURI(),
            request.getRemoteAddr(),
            ex.getMessage()
        );
        
        switch (severity) {
            case CRITICAL:
                log.error(message, ex);
                break;
            case HIGH:
                log.error(message);
                break;
            case MEDIUM:
                log.warn(message);
                break;
            case LOW:
                log.info(message);
                break;
        }
    }
    
    private void logValidationError(Map<String, List<String>> errors, WebRequest request) {
        log.warn("Validation failed for request [{}]: {}", 
            getPath(request), errors);
    }
    
    private void logValidationError(Map<String, List<String>> errors, HttpServletRequest request) {
        log.warn("Validation failed for request [{}]: {}", 
            request.getRequestURI(), errors);
    }
    
    private void logSecurityError(Exception ex, HttpServletRequest request, String type) {
        log.error("Security error [{}] for request [{}] from [{}]: {}", 
            type, request.getRequestURI(), request.getRemoteAddr(), ex.getMessage());
    }
    
    private void recordMetrics(String errorType, String errorCode) {
        metricsService.incrementErrorCounter(errorType, errorCode);
    }
    
    private String getTraceId() {
        // Get trace ID from MDC or generate new one
        return UUID.randomUUID().toString();
    }
    
    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
    
    private String getPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
    
    private boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }
    
    private String getStackTraceAsString(Exception ex) {
        // PCI DSS FIX: Build stack trace without printStackTrace()
        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");

        for (StackTraceElement element : ex.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.getClass().getName())
              .append(": ").append(cause.getMessage()).append("\n");
        }

        return sb.toString();
    }
    
    // Error Response Model
    @lombok.Data
    @lombok.Builder
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private String traceId;
        private String errorCode;
        private String errorId;
        private Map<String, List<String>> validationErrors;
        private String duplicateField;
        private Integer retryAfter;
        private Integer remainingLimit;
        private Long maxSize;
        private String service;
        private String paymentProvider;
        private BigDecimal availableBalance;
        private BigDecimal requiredAmount;
        private String stackTrace;
        private Map<String, Object> metadata;
    }
    
    // Error Severity Levels
    public enum ErrorSeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }
}