package com.waqiti.frauddetection.exception;

import com.waqiti.common.exception.ErrorResponse;
import com.waqiti.common.logging.ErrorMessageSanitizer;
import com.waqiti.common.alerting.AlertingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY: Global Exception Handler for Fraud Detection Service
 *
 * This class handles all exceptions thrown in the fraud-detection-service, ensuring:
 * 1. Sensitive fraud detection logic is NEVER leaked in error messages
 * 2. All errors are properly logged for security analysis
 * 3. Appropriate HTTP status codes are returned
 * 4. Correlation IDs are maintained for tracing
 * 5. Critical errors trigger alerts
 *
 * Security Considerations:
 * - Never expose ML model details or fraud detection rules
 * - Sanitize all error messages before returning to clients
 * - Log detailed errors internally for forensics
 * - Alert on critical fraud detection failures
 *
 * @author Waqiti Security Team
 * @version 1.0
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalFraudExceptionHandler {

    private final ErrorMessageSanitizer errorMessageSanitizer;
    private final AlertingService alertingService;

    /**
     * Handle fraud detection specific exceptions
     */
    @ExceptionHandler(FraudDetectionException.class)
    public ResponseEntity<ErrorResponse> handleFraudDetectionException(
            FraudDetectionException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.error("SECURITY: Fraud detection error - CorrelationID: {}, Type: {}, Message: {}",
                correlationId, ex.getClass().getSimpleName(), ex.getMessage(), ex);

        // CRITICAL: Sanitize error message to prevent fraud logic leakage
        String sanitizedMessage = errorMessageSanitizer.sanitize(ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Fraud Detection Error")
                .message("An error occurred during fraud analysis. Please contact support if this persists.")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // Alert on fraud detection failures
        alertingService.sendFraudDetectionAlert(
                "Fraud Detection Exception",
                ex.getMessage(),
                AlertingService.Severity.ERROR,
                Map.of(
                        "correlationId", correlationId,
                        "exceptionType", ex.getClass().getSimpleName(),
                        "path", request.getDescription(false)
                )
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Handle ML model errors - CRITICAL SECURITY
     * Never expose model architecture or parameters
     */
    @ExceptionHandler(MLModelException.class)
    public ResponseEntity<ErrorResponse> handleMLModelException(
            MLModelException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.error("CRITICAL SECURITY: ML Model error - CorrelationID: {}, ModelID: {}, Error: {}",
                correlationId, ex.getModelId(), ex.getMessage(), ex);

        // CRITICAL: Generic error message - never expose model details
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Temporarily Unavailable")
                .message("Fraud detection service is temporarily unavailable. Please try again later.")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // CRITICAL ALERT: ML model failures are high priority
        alertingService.sendFraudDetectionAlert(
                "ML Model Failure - CRITICAL",
                "ML model " + ex.getModelId() + " failed: " + ex.getMessage(),
                AlertingService.Severity.CRITICAL,
                Map.of(
                        "correlationId", correlationId,
                        "modelId", ex.getModelId(),
                        "errorType", ex.getClass().getSimpleName()
                )
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Handle external API errors (OFAC, sanctions lists, etc.)
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorResponse> handleExternalApiException(
            ExternalApiException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.error("SECURITY: External API error - CorrelationID: {}, API: {}, Status: {}",
                correlationId, ex.getApiName(), ex.getStatusCode(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("External Service Error")
                .message("A required external service is temporarily unavailable. Transaction may be delayed.")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // Alert if external API is down (affects fraud detection capability)
        alertingService.sendFraudDetectionAlert(
                "External API Failure",
                ex.getApiName() + " returned status: " + ex.getStatusCode(),
                AlertingService.Severity.WARNING,
                Map.of(
                        "correlationId", correlationId,
                        "apiName", ex.getApiName(),
                        "statusCode", String.valueOf(ex.getStatusCode())
                )
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        Map<String, String> validationErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));

        log.warn("SECURITY: Validation error - CorrelationID: {}, Errors: {}",
                correlationId, validationErrors);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Request validation failed")
                .correlationId(correlationId)
                .validationErrors(validationErrors)
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle access denied errors
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.warn("SECURITY ALERT: Access denied - CorrelationID: {}, Path: {}",
                correlationId, request.getDescription(false));

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Access Denied")
                .message("You do not have permission to access this resource")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // Alert on potential unauthorized access attempts
        alertingService.sendSecurityAlert(
                "Access Denied - Fraud Detection Service",
                "Unauthorized access attempt detected",
                AlertingService.Severity.WARNING,
                Map.of("correlationId", correlationId, "path", request.getDescription(false))
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Handle resource not found errors
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.info("Resource not found - CorrelationID: {}, Resource: {}",
                correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Resource Not Found")
                .message(errorMessageSanitizer.sanitize(ex.getMessage()))
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Handle timeout errors
     */
    @ExceptionHandler(java.util.concurrent.TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(
            java.util.concurrent.TimeoutException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.error("PERFORMANCE: Timeout error - CorrelationID: {}", correlationId, ex);

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.GATEWAY_TIMEOUT.value())
                .error("Request Timeout")
                .message("The fraud detection analysis took too long. Please try again.")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // Alert on timeouts (may indicate performance issues)
        alertingService.sendFraudDetectionAlert(
                "Fraud Detection Timeout",
                "Request timeout in fraud detection service",
                AlertingService.Severity.WARNING,
                Map.of("correlationId", correlationId)
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error);
    }

    /**
     * Handle circuit breaker open errors
     */
    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerException(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.error("CRITICAL: Circuit breaker open - CorrelationID: {}, CB: {}",
                correlationId, ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Temporarily Unavailable")
                .message("Fraud detection service is experiencing high load. Please try again shortly.")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // CRITICAL ALERT: Circuit breaker open
        alertingService.sendFraudDetectionAlert(
                "Circuit Breaker Open - CRITICAL",
                "Circuit breaker opened: " + ex.getMessage(),
                AlertingService.Severity.CRITICAL,
                Map.of("correlationId", correlationId, "circuitBreaker", ex.getMessage())
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    /**
     * Handle all other unexpected errors
     * CRITICAL: This is the last line of defense against information leakage
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {

        String correlationId = UUID.randomUUID().toString();

        log.error("CRITICAL: Unexpected error - CorrelationID: {}, Type: {}, Message: {}",
                correlationId, ex.getClass().getName(), ex.getMessage(), ex);

        // CRITICAL: Generic error message - never expose internal details
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Our team has been notified.")
                .correlationId(correlationId)
                .path(request.getDescription(false))
                .build();

        // CRITICAL ALERT: Unexpected errors in fraud detection service
        alertingService.sendFraudDetectionAlert(
                "Unexpected Error in Fraud Detection - CRITICAL",
                ex.getClass().getName() + ": " + ex.getMessage(),
                AlertingService.Severity.CRITICAL,
                Map.of(
                        "correlationId", correlationId,
                        "exceptionType", ex.getClass().getName(),
                        "path", request.getDescription(false)
                )
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ========== CUSTOM EXCEPTION CLASSES ==========

    /**
     * Base exception for fraud detection errors
     */
    public static class FraudDetectionException extends RuntimeException {
        public FraudDetectionException(String message) {
            super(message);
        }

        public FraudDetectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception for ML model errors
     */
    @lombok.Getter
    public static class MLModelException extends FraudDetectionException {
        private final String modelId;

        public MLModelException(String modelId, String message) {
            super(message);
            this.modelId = modelId;
        }

        public MLModelException(String modelId, String message, Throwable cause) {
            super(message, cause);
            this.modelId = modelId;
        }
    }

    /**
     * Exception for external API errors
     */
    @lombok.Getter
    public static class ExternalApiException extends FraudDetectionException {
        private final String apiName;
        private final int statusCode;

        public ExternalApiException(String apiName, int statusCode, String message) {
            super(message);
            this.apiName = apiName;
            this.statusCode = statusCode;
        }

        public ExternalApiException(String apiName, int statusCode, String message, Throwable cause) {
            super(message, cause);
            this.apiName = apiName;
            this.statusCode = statusCode;
        }
    }

    /**
     * Exception for resource not found errors
     */
    public static class ResourceNotFoundException extends FraudDetectionException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
