package com.waqiti.compliance.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for compliance service
 * Provides secure error handling without exposing sensitive information
 */
@ControllerAdvice
@Slf4j
public class GlobalComplianceExceptionHandler {
    
    /**
     * Handle validation errors from @Valid annotations
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
        });
        
        log.warn("Validation error [{}]: {} field errors", correlationId, fieldErrors.size());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Request validation failed")
            .path(request.getDescription(false))
            .validationErrors(fieldErrors)
            .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle constraint validation errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        Map<String, String> violations = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            violations.put(fieldName, violation.getMessage());
        }
        
        log.warn("Constraint violation [{}]: {} violations", correlationId, violations.size());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Constraint Violation")
            .message("Request constraint validation failed")
            .path(request.getDescription(false))
            .validationErrors(violations)
            .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle AML screening specific exceptions
     */
    @ExceptionHandler(ComplianceScreeningException.class)
    public ResponseEntity<ErrorResponse> handleComplianceScreeningException(ComplianceScreeningException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.error("Compliance screening error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Compliance Screening Error")
            .message("Transaction screening failed")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Handle compliance check exceptions
     */
    @ExceptionHandler(ComplianceCheckException.class)
    public ResponseEntity<ErrorResponse> handleComplianceCheckException(ComplianceCheckException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.error("Compliance check error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .error("Compliance Check Failed")
            .message("Compliance validation failed")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }
    
    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.warn("Access denied [{}]: {}", correlationId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Access Denied")
            .message("Insufficient permissions for this operation")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Handle malformed JSON requests
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.warn("Malformed request [{}]: {}", correlationId, ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Malformed Request")
            .message("Request body is not valid JSON")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.warn("Argument type mismatch [{}]: parameter '{}' with value '{}' could not be converted to type '{}'", 
                correlationId, ex.getName(), ex.getValue(), ex.getRequiredType());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Parameter Type")
            .message(String.format("Parameter '%s' has invalid format", ex.getName()))
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Handle database-related exceptions
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccessException(org.springframework.dao.DataAccessException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.error("Database error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Database Error")
            .message("A database error occurred while processing your request")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Handle Kafka messaging exceptions
     */
    @ExceptionHandler(org.springframework.kafka.KafkaException.class)
    public ResponseEntity<ErrorResponse> handleKafkaException(org.springframework.kafka.KafkaException ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.error("Kafka messaging error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("Messaging Service Error")
            .message("A messaging service error occurred")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
    
    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        String correlationId = generateCorrelationId();
        
        log.error("Unexpected error [{}]: {}", correlationId, ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .correlationId(correlationId)
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error("Internal Server Error")
            .message("An unexpected error occurred while processing your request")
            .path(request.getDescription(false))
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
    
    /**
     * Generate correlation ID for error tracking
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * Standard error response format
     */
    @lombok.Data
    @lombok.Builder
    public static class ErrorResponse {
        private String correlationId;
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private Map<String, String> validationErrors;
    }
}