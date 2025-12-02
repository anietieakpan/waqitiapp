package com.waqiti.validation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global exception handler for validation errors
 * 
 * Provides consistent error responses across all financial services
 * with detailed field-level validation information and security logging
 */
@Slf4j
@RestControllerAdvice
public class GlobalValidationExceptionHandler {
    
    /**
     * Handle custom validation exceptions
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        log.warn("SECURITY: Validation exception in operation: {}, errors: {}", 
            ex.getOperation(), ex.getMessage());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode())
            .operation(ex.getOperation())
            .path(request.getDescription(false))
            .fieldErrors(convertFieldErrors(ex.getFieldErrors()))
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle method argument validation errors (from @Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                      .add(error.getDefaultMessage());
        }
        
        log.warn("SECURITY: Method argument validation failed with {} field errors", 
            fieldErrors.size());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Request Data")
            .message("Request validation failed")
            .errorCode("METHOD_ARGUMENT_NOT_VALID")
            .path(request.getDescription(false))
            .fieldErrors(fieldErrors)
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle constraint violation exceptions (from @Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>())
                      .add(violation.getMessage());
        }
        
        log.warn("SECURITY: Constraint validation failed with {} violations", 
            ex.getConstraintViolations().size());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Constraint Violation")
            .message("Validation constraints were violated")
            .errorCode("CONSTRAINT_VIOLATION")
            .path(request.getDescription(false))
            .fieldErrors(fieldErrors)
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle bind exceptions (form data binding errors)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ValidationErrorResponse> handleBindException(
            BindException ex, WebRequest request) {
        
        Map<String, List<String>> fieldErrors = new HashMap<>();
        
        for (FieldError error : ex.getFieldErrors()) {
            fieldErrors.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                      .add(error.getDefaultMessage());
        }
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Binding Error")
            .message("Data binding validation failed")
            .errorCode("BIND_EXCEPTION")
            .path(request.getDescription(false))
            .fieldErrors(fieldErrors)
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle HTTP message not readable (JSON parsing errors)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        
        log.warn("SECURITY: HTTP message not readable: {}", ex.getMessage());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Request Format")
            .message("Request body is malformed or unreadable")
            .errorCode("MESSAGE_NOT_READABLE")
            .path(request.getDescription(false))
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ValidationErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("SECURITY: Illegal argument exception: {}", ex.getMessage());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Argument")
            .message(ex.getMessage())
            .errorCode("ILLEGAL_ARGUMENT")
            .path(request.getDescription(false))
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Handle number format exceptions (amount parsing errors)
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ValidationErrorResponse> handleNumberFormat(
            NumberFormatException ex, WebRequest request) {
        
        log.warn("SECURITY: Number format exception: {}", ex.getMessage());
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Invalid Number Format")
            .message("One or more numeric values are incorrectly formatted")
            .errorCode("NUMBER_FORMAT_ERROR")
            .path(request.getDescription(false))
            .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * Convert ValidationException field errors to response format
     */
    private Map<String, List<String>> convertFieldErrors(
            List<ValidationException.FieldError> fieldErrors) {
        
        return fieldErrors.stream()
            .collect(Collectors.groupingBy(
                ValidationException.FieldError::getField,
                Collectors.mapping(ValidationException.FieldError::getMessage, 
                                 Collectors.toList())
            ));
    }
    
    /**
     * Validation error response DTO
     */
    public static class ValidationErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String errorCode;
        private String operation;
        private String path;
        private Map<String, List<String>> fieldErrors;
        private Map<String, Object> metadata;
        
        // Builder pattern implementation
        public static ValidationErrorResponseBuilder builder() {
            return new ValidationErrorResponseBuilder();
        }
        
        public static class ValidationErrorResponseBuilder {
            private final ValidationErrorResponse response = new ValidationErrorResponse();
            
            public ValidationErrorResponseBuilder timestamp(LocalDateTime timestamp) {
                response.timestamp = timestamp;
                return this;
            }
            
            public ValidationErrorResponseBuilder status(int status) {
                response.status = status;
                return this;
            }
            
            public ValidationErrorResponseBuilder error(String error) {
                response.error = error;
                return this;
            }
            
            public ValidationErrorResponseBuilder message(String message) {
                response.message = message;
                return this;
            }
            
            public ValidationErrorResponseBuilder errorCode(String errorCode) {
                response.errorCode = errorCode;
                return this;
            }
            
            public ValidationErrorResponseBuilder operation(String operation) {
                response.operation = operation;
                return this;
            }
            
            public ValidationErrorResponseBuilder path(String path) {
                response.path = path;
                return this;
            }
            
            public ValidationErrorResponseBuilder fieldErrors(Map<String, List<String>> fieldErrors) {
                response.fieldErrors = fieldErrors;
                return this;
            }
            
            public ValidationErrorResponseBuilder metadata(Map<String, Object> metadata) {
                response.metadata = metadata;
                return this;
            }
            
            public ValidationErrorResponse build() {
                return response;
            }
        }
        
        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getError() { return error; }
        public String getMessage() { return message; }
        public String getErrorCode() { return errorCode; }
        public String getOperation() { return operation; }
        public String getPath() { return path; }
        public Map<String, List<String>> getFieldErrors() { return fieldErrors; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}