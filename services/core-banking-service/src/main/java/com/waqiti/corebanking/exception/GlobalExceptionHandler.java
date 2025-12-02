package com.waqiti.corebanking.exception;

import com.waqiti.corebanking.client.FeignClientConfiguration;
import com.waqiti.corebanking.dto.ErrorResponseDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers
 * Provides consistent error responses across the application
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid annotation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        log.error("Validation error on path {}: {}", request.getRequestURI(), ex.getMessage());

        List<ErrorResponseDto.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponseDto.FieldError.builder()
                        .field(error.getField())
                        .rejectedValue(error.getRejectedValue())
                        .message(error.getDefaultMessage())
                        .code(error.getCode())
                        .build())
                .collect(Collectors.toList());

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed for request")
                .details(String.format("%d validation error(s) found", fieldErrors.size()))
                .path(request.getRequestURI())
                .errorCode("VALIDATION_ERROR")
                .fieldErrors(fieldErrors)
                .correlationId(java.util.UUID.randomUUID().toString())
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.error("Constraint violation on path {}: {}", request.getRequestURI(), ex.getMessage());

        List<ErrorResponseDto.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ErrorResponseDto.FieldError.builder()
                        .field(getFieldName(violation))
                        .rejectedValue(violation.getInvalidValue())
                        .message(violation.getMessage())
                        .code(violation.getMessageTemplate())
                        .build())
                .collect(Collectors.toList());

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Constraint violation")
                .path(request.getRequestURI())
                .errorCode("CONSTRAINT_VIOLATION")
                .fieldErrors(fieldErrors)
                .correlationId(java.util.UUID.randomUUID().toString())
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle HTTP message not readable (malformed JSON)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.error("Malformed request on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Malformed JSON request",
                ex.getMostSpecificCause().getMessage(),
                request.getRequestURI()
        );
        errorResponse.setErrorCode("MALFORMED_JSON");

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParams(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.error("Missing parameter on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                String.format("Required parameter '%s' is missing", ex.getParameterName()),
                request.getRequestURI(),
                "MISSING_PARAMETER"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle type mismatch errors
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.error("Type mismatch on path {}: {}", request.getRequestURI(), ex.getMessage());

        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI(),
                "TYPE_MISMATCH"
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle authentication errors
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        log.error("Authentication error on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication failed",
                request.getRequestURI(),
                "AUTHENTICATION_FAILED"
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handle authorization errors
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.error("Access denied on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Access denied - insufficient permissions",
                request.getRequestURI(),
                "ACCESS_DENIED"
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle entity not found errors
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleEntityNotFoundException(
            EntityNotFoundException ex,
            HttpServletRequest request) {

        log.error("Entity not found on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "Requested resource not found",
                ex.getMessage(),
                request.getRequestURI()
        );
        errorResponse.setErrorCode("RESOURCE_NOT_FOUND");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle data integrity violations (unique constraints, FK violations)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.error("Data integrity violation on path {}: {}", request.getRequestURI(), ex.getMessage());

        String message = "Data integrity violation";
        String errorCode = "DATA_INTEGRITY_VIOLATION";

        // Check for duplicate key violation
        if (ex.getMessage() != null && ex.getMessage().contains("duplicate key")) {
            message = "Duplicate entry detected - resource already exists";
            errorCode = "DUPLICATE_ENTRY";
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                message,
                request.getRequestURI(),
                errorCode
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle optimistic locking failures
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDto> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {

        log.error("Optimistic locking failure on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Resource was modified by another user - please refresh and try again",
                request.getRequestURI(),
                "OPTIMISTIC_LOCK_FAILURE"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle database access errors
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponseDto> handleDataAccessException(
            DataAccessException ex,
            HttpServletRequest request) {

        log.error("Database error on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Database operation failed",
                request.getRequestURI(),
                "DATABASE_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle HTTP method not supported
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        log.error("Method not supported on path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                String.format("HTTP %s method not supported for this endpoint", ex.getMethod()),
                request.getRequestURI(),
                "METHOD_NOT_ALLOWED"
        );

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    /**
     * Handle no handler found (404)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {

        log.error("No handler found for path {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "Endpoint not found",
                request.getRequestURI(),
                "ENDPOINT_NOT_FOUND"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle Feign client exceptions
     */
    @ExceptionHandler(FeignClientConfiguration.ServiceClientException.class)
    public ResponseEntity<ErrorResponseDto> handleServiceClientException(
            FeignClientConfiguration.ServiceClientException ex,
            HttpServletRequest request) {

        log.error("Service client error on path {}: {}", request.getRequestURI(), ex.getMessage());

        HttpStatus status = determineStatusFromServiceException(ex);

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                status.value(),
                status.getReasonPhrase(),
                "External service call failed",
                ex.getMessage(),
                request.getRequestURI()
        );
        errorResponse.setErrorCode("SERVICE_CALL_FAILED");

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle business exceptions from core banking
     */
    @ExceptionHandler({
            CoreBankingExceptions.class,
            BankAccountExceptions.class,
            ComplianceValidationException.class,
            TransactionReversalException.class,
            TransactionStatusUpdateException.class,
            AccountResolutionException.class,
            ExchangeRateException.class,
            StatementJobCreationException.class,
            StatementJobNotFoundException.class
    })
    public ResponseEntity<ErrorResponseDto> handleBusinessException(
            RuntimeException ex,
            HttpServletRequest request) {

        log.error("Business error on path {}: {}", request.getRequestURI(), ex.getMessage());

        // Determine HTTP status based on exception type
        HttpStatus status = determineStatusFromBusinessException(ex);

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                getErrorCodeFromException(ex)
        );

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle all other unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception on path {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponseDto errorResponse = ErrorResponseDto.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred",
                request.getRequestURI(),
                "INTERNAL_SERVER_ERROR"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // Helper methods

    private String getFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private HttpStatus determineStatusFromServiceException(FeignClientConfiguration.ServiceClientException ex) {
        if (ex instanceof FeignClientConfiguration.ServiceClientException.BadRequestException) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof FeignClientConfiguration.ServiceClientException.UnauthorizedException) {
            return HttpStatus.UNAUTHORIZED;
        } else if (ex instanceof FeignClientConfiguration.ServiceClientException.ForbiddenException) {
            return HttpStatus.FORBIDDEN;
        } else if (ex instanceof FeignClientConfiguration.ServiceClientException.NotFoundException) {
            return HttpStatus.NOT_FOUND;
        } else if (ex instanceof FeignClientConfiguration.ServiceClientException.ServerException) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private HttpStatus determineStatusFromBusinessException(RuntimeException ex) {
        String exName = ex.getClass().getSimpleName();

        if (exName.contains("NotFound") || exName.contains("NotExists")) {
            return HttpStatus.NOT_FOUND;
        } else if (exName.contains("Invalid") || exName.contains("Validation")) {
            return HttpStatus.BAD_REQUEST;
        } else if (exName.contains("InsufficientFunds") || exName.contains("Insufficient")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        } else if (exName.contains("Compliance") || exName.contains("Forbidden")) {
            return HttpStatus.FORBIDDEN;
        } else if (exName.contains("Conflict") || exName.contains("Duplicate")) {
            return HttpStatus.CONFLICT;
        }

        return HttpStatus.BAD_REQUEST;
    }

    private String getErrorCodeFromException(RuntimeException ex) {
        String className = ex.getClass().getSimpleName();
        // Convert camelCase to SNAKE_CASE
        return className
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("Exception$", "")
                .toUpperCase();
    }
}
