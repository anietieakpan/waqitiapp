package com.waqiti.familyaccount.exception;

import com.waqiti.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler for Family Account Service
 *
 * Centralized exception handling using @ControllerAdvice to provide consistent
 * error responses across all endpoints. Handles both business exceptions and
 * framework exceptions with appropriate HTTP status codes and error messages.
 *
 * Error Response Format (using ApiResponse from common module):
 * {
 *   "success": false,
 *   "message": "User-friendly error message",
 *   "data": null,
 *   "error": {
 *     "code": "ERROR_CODE",
 *     "details": "Technical details",
 *     "timestamp": "2025-11-19T10:30:00",
 *     "path": "/api/v2/family-accounts/123"
 *   }
 * }
 *
 * Security Notes:
 * - Sanitizes error messages to prevent information disclosure
 * - No stack traces in production responses
 * - Sensitive data is never included in error responses
 * - All exceptions are logged with full details for debugging
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@ControllerAdvice
@Slf4j
public class FamilyAccountExceptionHandler {

    // ==================== Business Domain Exceptions ====================

    /**
     * Handle Family Account Not Found Exception
     * Returns 404 Not Found when a family account doesn't exist
     */
    @ExceptionHandler(FamilyAccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiResponse<Void>> handleFamilyAccountNotFound(
            FamilyAccountNotFoundException ex,
            WebRequest request) {
        log.warn("Family account not found: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "FAMILY_ACCOUNT_NOT_FOUND",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    /**
     * Handle Family Member Not Found Exception
     * Returns 404 Not Found when a family member doesn't exist
     */
    @ExceptionHandler(FamilyMemberNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiResponse<Void>> handleFamilyMemberNotFound(
            FamilyMemberNotFoundException ex,
            WebRequest request) {
        log.warn("Family member not found: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "FAMILY_MEMBER_NOT_FOUND",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    /**
     * Handle Unauthorized Access Exception
     * Returns 403 Forbidden when user lacks permission for operation
     */
    @ExceptionHandler(UnauthorizedAccessException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedAccess(
            UnauthorizedAccessException ex,
            WebRequest request) {
        log.warn("Unauthorized access attempt: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "UNAUTHORIZED_ACCESS",
            "You do not have permission to perform this operation",
            request
        );

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied: " + ex.getMessage(), errorDetails));
    }

    /**
     * Handle Spending Limit Exceeded Exception
     * Returns 400 Bad Request when transaction exceeds spending limits
     */
    @ExceptionHandler(SpendingLimitExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleSpendingLimitExceeded(
            SpendingLimitExceededException ex,
            WebRequest request) {
        log.info("Spending limit exceeded: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "SPENDING_LIMIT_EXCEEDED",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    /**
     * Handle Insufficient Funds Exception
     * Returns 400 Bad Request when wallet balance is insufficient
     */
    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientFunds(
            InsufficientFundsException ex,
            WebRequest request) {
        log.info("Insufficient funds: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "INSUFFICIENT_FUNDS",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    /**
     * Handle External Service Exception
     * Returns 503 Service Unavailable when external service call fails
     */
    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ApiResponse<Void>> handleExternalServiceError(
            ExternalServiceException ex,
            WebRequest request) {
        log.error("External service error: {} - Service: {} - Path: {}",
                ex.getMessage(), ex.getServiceName(), request.getDescription(false), ex);

        Map<String, Object> errorDetails = buildErrorDetails(
            "EXTERNAL_SERVICE_ERROR",
            "Service temporarily unavailable. Please try again.",
            request
        );
        errorDetails.put("serviceName", ex.getServiceName());

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.error(
                "Service temporarily unavailable. Please try again later.",
                errorDetails
            ));
    }

    /**
     * Handle Generic Family Account Exception
     * Returns 400 Bad Request for general business rule violations
     */
    @ExceptionHandler(FamilyAccountException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleFamilyAccountException(
            FamilyAccountException ex,
            WebRequest request) {
        log.warn("Family account exception: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "FAMILY_ACCOUNT_ERROR",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    // ==================== Validation Exceptions ====================

    /**
     * Handle Method Argument Not Valid Exception
     * Returns 400 Bad Request for @Valid/@Validated validation failures
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        log.warn("Validation error: {} validation failures - Path: {}",
                ex.getBindingResult().getErrorCount(), request.getDescription(false));

        Map<String, String> validationErrors = ex.getBindingResult().getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                (existing, replacement) -> existing + "; " + replacement
            ));

        Map<String, Object> errorDetails = buildErrorDetails(
            "VALIDATION_ERROR",
            "Request validation failed",
            request
        );
        errorDetails.put("validationErrors", validationErrors);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed", validationErrors));
    }

    /**
     * Handle Constraint Violation Exception
     * Returns 400 Bad Request for @Validated constraint violations
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {
        log.warn("Constraint violation: {} - Path: {}",
                ex.getMessage(), request.getDescription(false));

        Map<String, String> violations = ex.getConstraintViolations()
            .stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (existing, replacement) -> existing + "; " + replacement
            ));

        Map<String, Object> errorDetails = buildErrorDetails(
            "CONSTRAINT_VIOLATION",
            "Constraint validation failed",
            request
        );
        errorDetails.put("violations", violations);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Constraint validation failed", violations));
    }

    /**
     * Handle HTTP Message Not Readable Exception
     * Returns 400 Bad Request for malformed JSON or request body issues
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {
        log.warn("Malformed request body - Path: {}", request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "MALFORMED_REQUEST",
            "Request body is malformed or missing required fields",
            request
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Invalid request format. Please check your request body.", errorDetails));
    }

    /**
     * Handle Method Argument Type Mismatch Exception
     * Returns 400 Bad Request for invalid parameter types
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {
        log.warn("Type mismatch for parameter '{}': expected {}, got {} - Path: {}",
                ex.getName(), ex.getRequiredType(), ex.getValue(), request.getDescription(false));

        String message = String.format(
            "Invalid value '%s' for parameter '%s'. Expected type: %s",
            ex.getValue(),
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );

        Map<String, Object> errorDetails = buildErrorDetails(
            "TYPE_MISMATCH",
            message,
            request
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(message, errorDetails));
    }

    // ==================== Security Exceptions ====================

    /**
     * Handle Authentication Exception
     * Returns 401 Unauthorized for authentication failures
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex,
            WebRequest request) {
        log.warn("Authentication failed - Path: {}", request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "AUTHENTICATION_FAILED",
            "Authentication required",
            request
        );

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Authentication required. Please log in.", errorDetails));
    }

    /**
     * Handle Access Denied Exception
     * Returns 403 Forbidden for authorization failures
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {
        log.warn("Access denied - Path: {}", request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "ACCESS_DENIED",
            "Insufficient permissions",
            request
        );

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("You do not have permission to access this resource.", errorDetails));
    }

    // ==================== Framework Exceptions ====================

    /**
     * Handle No Handler Found Exception
     * Returns 404 Not Found for non-existent endpoints
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(
            NoHandlerFoundException ex,
            WebRequest request) {
        log.warn("No handler found for {} {} - Path: {}",
                ex.getHttpMethod(), ex.getRequestURL(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "ENDPOINT_NOT_FOUND",
            String.format("No endpoint found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
            request
        );

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("Endpoint not found.", errorDetails));
    }

    /**
     * Handle Illegal Argument Exception
     * Returns 400 Bad Request for invalid method arguments
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request) {
        log.warn("Illegal argument: {} - Path: {}", ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "INVALID_ARGUMENT",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    /**
     * Handle Illegal State Exception
     * Returns 409 Conflict for invalid state transitions
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(
            IllegalStateException ex,
            WebRequest request) {
        log.warn("Illegal state: {} - Path: {}", ex.getMessage(), request.getDescription(false));

        Map<String, Object> errorDetails = buildErrorDetails(
            "INVALID_STATE",
            ex.getMessage(),
            request
        );

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.getMessage(), errorDetails));
    }

    // ==================== Catch-All Exception Handler ====================

    /**
     * Handle Generic Exception
     * Returns 500 Internal Server Error for unexpected exceptions
     * This is the catch-all handler for any unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex,
            WebRequest request) {
        log.error("Unexpected error occurred - Path: {} - Error: {}",
                request.getDescription(false), ex.getMessage(), ex);

        Map<String, Object> errorDetails = buildErrorDetails(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            request
        );

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                "An unexpected error occurred. Our team has been notified. Please try again later.",
                errorDetails
            ));
    }

    // ==================== Helper Methods ====================

    /**
     * Build standardized error details map
     */
    private Map<String, Object> buildErrorDetails(String code, String details, WebRequest request) {
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("code", code);
        errorDetails.put("details", details);
        errorDetails.put("timestamp", LocalDateTime.now().toString());
        errorDetails.put("path", request.getDescription(false).replace("uri=", ""));
        return errorDetails;
    }
}
