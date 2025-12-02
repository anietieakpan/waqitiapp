package com.waqiti.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global exception handler for consistent error responses
 * Provides comprehensive error handling with security considerations
 */
@ControllerAdvice
@Slf4j
public class ExceptionHandler {

    /**
     * Handle validation errors
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Invalid input parameters")
                .validationErrors(errors)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Validation error: {}", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle constraint violation exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {
        
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing
                ));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Validation constraints violated")
                .validationErrors(errors)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Constraint violation: {}", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle type mismatch exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {
        
        String error = String.format("Parameter '%s' should be of type %s",
                ex.getName(), ex.getRequiredType().getSimpleName());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Type Mismatch")
                .message(error)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Type mismatch error: {}", error);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle access denied exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Access Denied")
                .message("You don't have permission to access this resource")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Access denied: {} - {}", request.getUserPrincipal(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle business logic exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .error(ex.getErrorCode())
                .message(ex.getMessage())
                .details(ex.getDetails())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    /**
     * Handle resource not found exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Resource Not Found")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Resource not found: {}", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle duplicate resource exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Duplicate Resource")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Duplicate resource: {}", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle invalid state exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(
            InvalidStateException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                .error("Invalid State")
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Invalid state: {}", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Handle integration exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(IntegrationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrationException(
            IntegrationException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Integration Error")
                .message("External service integration failed")
                .details(Map.of("service", ex.getServiceName(), "error", ex.getMessage()))
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.error("Integration error with {}: {}", ex.getServiceName(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Handle rate limit exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitException ex,
            WebRequest request) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Rate Limit Exceeded")
                .message(ex.getMessage())
                .details(Map.of(
                        "retryAfter", ex.getRetryAfter(),
                        "limit", ex.getLimit()
                ))
                .path(request.getDescription(false).replace("uri=", ""))
                .build();
        
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Handle JPA optimistic locking exceptions - CRITICAL for financial operations
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(jakarta.persistence.OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(
            jakarta.persistence.OptimisticLockException ex,
            WebRequest request) {

        String entityName = ex.getEntity() != null ? ex.getEntity().getClass().getSimpleName() : "Unknown";

        log.warn("OPTIMISTIC_LOCK_FAILURE | entity={} | path={} | message={}",
                entityName,
                request.getDescription(false),
                ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("CONCURRENT_MODIFICATION")
                .message("The resource was modified by another request. Please refresh and try again.")
                .details(Map.of(
                        "entity", entityName,
                        "suggestion", "Retrieve the latest version and retry your operation"
                ))
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle Spring's optimistic locking failures
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleSpringOptimisticLock(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex,
            WebRequest request) {

        String entityName = ex.getPersistentClassName();

        log.warn("SPRING_OPTIMISTIC_LOCK_FAILURE | entity={} | identifier={} | path={}",
                entityName,
                ex.getIdentifier(),
                request.getDescription(false));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("CONCURRENT_MODIFICATION")
                .message("Resource was updated by another transaction. Please retry your operation.")
                .details(Map.of(
                        "entity", entityName != null ? entityName : "Unknown",
                        "entityId", ex.getIdentifier() != null ? String.valueOf(ex.getIdentifier()) : "Unknown"
                ))
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle generic optimistic locking failures
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            org.springframework.dao.OptimisticLockingFailureException ex,
            WebRequest request) {

        log.warn("OPTIMISTIC_LOCKING_FAILURE | path={} | message={}",
                request.getDescription(false),
                ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("CONCURRENT_MODIFICATION")
                .message("The operation conflicted with another concurrent update. Please retry.")
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handle all other exceptions
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {

        // Log the full exception for debugging
        log.error("Unhandled exception occurred", ex);

        // Don't expose internal details in production
        String message = isProductionEnvironment()
                ? "An internal error occurred. Please try again later."
                : ex.getMessage();

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Check if running in production environment
     */
    private boolean isProductionEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "");
        return profile.contains("prod") || profile.contains("production");
    }

    /**
     * Error response DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private Map<String, String> validationErrors;
        private Map<String, Object> details;
        private String path;
        private String traceId;
        
        public ErrorResponse() {
            this.timestamp = LocalDateTime.now();
            this.traceId = UUID.randomUUID().toString();
        }
        
        public ErrorResponse(LocalDateTime timestamp, int status, String error, 
                           String message, Map<String, String> validationErrors,
                           Map<String, Object> details, String path, String traceId) {
            this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
            this.status = status;
            this.error = error;
            this.message = message;
            this.validationErrors = validationErrors;
            this.details = details;
            this.path = path;
            this.traceId = traceId != null ? traceId : UUID.randomUUID().toString();
        }
    }
}

/**
 * Invalid state exception
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) {
        super(message);
    }
}

/**
 * Integration exception
 */
class IntegrationException extends RuntimeException {
    private final String serviceName;
    
    public IntegrationException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }
    
    public String getServiceName() { return serviceName; }
}

/**
 * Rate limit exception
 */
class RateLimitException extends RuntimeException {
    private final int retryAfter;
    private final int limit;
    
    public RateLimitException(String message, int retryAfter, int limit) {
        super(message);
        this.retryAfter = retryAfter;
        this.limit = limit;
    }
    
    public int getRetryAfter() { return retryAfter; }
    public int getLimit() { return limit; }
}