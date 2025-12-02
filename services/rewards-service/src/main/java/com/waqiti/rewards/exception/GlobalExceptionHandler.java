package com.waqiti.rewards.exception;

import com.waqiti.rewards.dto.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler for all controllers
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${app.error.include-stacktrace:false}")
    private boolean includeStackTrace;

    /**
     * Handle validation errors from @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .rejectedValue(error.getRejectedValue())
                        .build())
                .collect(Collectors.toList());

        log.warn("Validation error: path={}, correlationId={}, errors={}",
                path, correlationId, fieldErrors.size());

        ErrorResponse response = ErrorResponse.validationError(
                "Validation failed for request",
                fieldErrors,
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle constraint violations from @Validated
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(violation -> ErrorResponse.FieldError.builder()
                        .field(getFieldName(violation))
                        .message(violation.getMessage())
                        .rejectedValue(violation.getInvalidValue())
                        .build())
                .collect(Collectors.toList());

        log.warn("Constraint violation: path={}, correlationId={}, errors={}",
                path, correlationId, fieldErrors.size());

        ErrorResponse response = ErrorResponse.validationError(
                "Validation constraints violated",
                fieldErrors,
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle referral program not found
     */
    @ExceptionHandler(ReferralProgramNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProgramNotFound(
            ReferralProgramNotFoundException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Program not found: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.notFound(
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle referral link not found
     */
    @ExceptionHandler(ReferralLinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLinkNotFound(
            ReferralLinkNotFoundException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Link not found: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.notFound(
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle referral reward not found
     */
    @ExceptionHandler(ReferralRewardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRewardNotFound(
            ReferralRewardNotFoundException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Reward not found: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.notFound(
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle budget exceeded
     */
    @ExceptionHandler(ReferralBudgetExceededException.class)
    public ResponseEntity<ErrorResponse> handleBudgetExceeded(
            ReferralBudgetExceededException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.error("Budget exceeded: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.businessRuleViolation(
                "BUDGET_EXCEEDED",
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handle referral code already exists
     */
    @ExceptionHandler(ReferralCodeAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCodeAlreadyExists(
            ReferralCodeAlreadyExistsException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Referral code exists: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.builder()
                .errorCode("CODE_ALREADY_EXISTS")
                .message(ex.getMessage())
                .status(409)
                .timestamp(java.time.Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle reward expired
     */
    @ExceptionHandler(ReferralRewardExpiredException.class)
    public ResponseEntity<ErrorResponse> handleRewardExpired(
            ReferralRewardExpiredException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Reward expired: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.businessRuleViolation(
                "REWARD_EXPIRED",
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handle referral ineligible
     */
    @ExceptionHandler(ReferralIneligibleException.class)
    public ResponseEntity<ErrorResponse> handleReferralIneligible(
            ReferralIneligibleException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Referral ineligible: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.businessRuleViolation(
                "REFERRAL_INELIGIBLE",
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handle data integrity violations (unique constraints, etc.)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.error("Data integrity violation: correlationId={}", correlationId, ex);

        String message = "Data integrity constraint violated";
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("unique")) {
                message = "Duplicate entry detected";
            } else if (ex.getMessage().contains("foreign key")) {
                message = "Referenced entity not found";
            }
        }

        ErrorResponse response = ErrorResponse.builder()
                .errorCode("DATA_INTEGRITY_VIOLATION")
                .message(message)
                .status(409)
                .timestamp(java.time.Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle type mismatch (e.g., invalid UUID format)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Type mismatch: parameter={}, value={}, correlationId={}",
                ex.getName(), ex.getValue(), correlationId);

        String message = String.format("Invalid value '%s' for parameter '%s'",
                ex.getValue(), ex.getName());

        ErrorResponse response = ErrorResponse.builder()
                .errorCode("INVALID_PARAMETER")
                .message(message)
                .status(400)
                .timestamp(java.time.Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle access denied (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Access denied: path={}, correlationId={}", path, correlationId);

        ErrorResponse response = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("You do not have permission to access this resource")
                .status(403)
                .timestamp(java.time.Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle illegal argument exceptions
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Illegal argument: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.builder()
                .errorCode("INVALID_ARGUMENT")
                .message(ex.getMessage())
                .status(400)
                .timestamp(java.time.Instant.now())
                .path(path)
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle illegal state exceptions
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.warn("Illegal state: message={}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponse response = ErrorResponse.businessRuleViolation(
                "ILLEGAL_STATE",
                ex.getMessage(),
                path,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String path = request.getRequestURI();

        log.error("Unhandled exception: path={}, correlationId={}",
                path, correlationId, ex);

        ErrorResponse response = ErrorResponse.internalError(
                "An unexpected error occurred. Please contact support with correlation ID: " + correlationId,
                path,
                correlationId
        );

        if (includeStackTrace) {
            response.setStackTrace(getStackTraceAsString(ex));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Get correlation ID from MDC or generate new one
     */
    private String getCorrelationId() {
        String correlationId = MDC.get("correlation_id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    /**
     * Extract field name from constraint violation
     */
    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    /**
     * Convert stack trace to string
     */
    private String getStackTraceAsString(Exception ex) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
