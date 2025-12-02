package com.waqiti.common.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.exception.InvalidPaymentOperationException;
import com.waqiti.common.exception.DuplicateResourceException;
import com.waqiti.common.exception.InsufficientFundsException;
import com.waqiti.common.exception.ExternalServiceException;
import com.waqiti.common.error.ErrorResponse;
import com.waqiti.common.security.SecurityViolationException;
import com.waqiti.common.security.hsm.exception.HSMException;
import com.waqiti.common.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.crypto.BadPaddingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Global error handler for comprehensive exception management across all services
 * Provides standardized error responses with proper logging and monitoring
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalErrorHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalErrorHandler.class);

    private final MessageSource messageSource;
    private final ErrorReportingService errorReportingService;
    private final SecurityAuditService securityAuditService;

    // ========================================
    // SECURITY-RELATED EXCEPTIONS
    // ========================================

    @ExceptionHandler(SecurityViolationException.class)
    public ResponseEntity<ErrorResponse> handleSecurityViolation(
            SecurityViolationException ex, WebRequest request) {
        
        String clientIp = extractClientIp(request);
        log.error("SECURITY VIOLATION from IP {}: {}", clientIp, ex.getMessage(), ex);
        
        // Report to security monitoring
        securityAuditService.reportSecurityViolation(
            clientIp, 
            ex.getViolationType(),
            ex.getMessage(),
            extractUserAgent(request)
        );
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Security Violation")
                .message("Access denied due to security policy violation")
                .path(extractPath(request))
                .code("SECURITY_VIOLATION")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, WebRequest request) {
        
        log.warn("Authentication failed: {} from IP: {}", ex.getMessage(), extractClientIp(request));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Authentication Failed")
                .message("Invalid credentials or authentication required")
                .path(extractPath(request))
                .code("AUTH_FAILED")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        log.warn("Access denied: {} for path: {}", ex.getMessage(), extractPath(request));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("Access Denied")
                .message("Insufficient permissions to access this resource")
                .path(extractPath(request))
                .code("ACCESS_DENIED")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {
        
        String clientIp = extractClientIp(request);
        log.warn("Bad credentials attempt from IP: {}", clientIp);
        
        // Track failed authentication attempts
        securityAuditService.recordFailedAuthentication(clientIp, extractUserAgent(request));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Invalid Credentials")
                .message("The provided credentials are invalid")
                .path(extractPath(request))
                .code("INVALID_CREDENTIALS")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // ========================================
    // BUSINESS LOGIC EXCEPTIONS
    // ========================================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        log.warn("Business logic error: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Business Logic Error")
                .message(ex.getMessage())
                .path(extractPath(request))
                .code(ex.getErrorCode())
                .traceId(generateTraceId())
                .details(ex.getDetails())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {
        
        log.info("Resource not found: {} for path: {}", ex.getMessage(), extractPath(request));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Resource Not Found")
                .message(ex.getMessage())
                .path(extractPath(request))
                .code("RESOURCE_NOT_FOUND")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, WebRequest request) {
        
        log.warn("Duplicate resource attempt: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Duplicate Resource")
                .message(ex.getMessage())
                .path(extractPath(request))
                .code("DUPLICATE_RESOURCE")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, WebRequest request) {
        
        log.warn("Insufficient funds: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.PAYMENT_REQUIRED.value())
                .error("Insufficient Funds")
                .message("Insufficient funds to complete this transaction")
                .path(extractPath(request))
                .code("INSUFFICIENT_FUNDS")
                .traceId(generateTraceId())
                .details(Map.of(
                    "availableBalance", ex.getAvailableBalance(),
                    "requiredAmount", ex.getRequiredAmount()
                ))
                .build();
        
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
    }

    // ========================================
    // VALIDATION EXCEPTIONS
    // ========================================

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            ValidationException ex, WebRequest request) {
        
        log.warn("Validation error: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Request validation failed")
                .path(extractPath(request))
                .code("VALIDATION_FAILED")
                .traceId(generateTraceId())
                .details(new HashMap<>(ex.getValidationErrors()))
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            validationErrors.put(fieldName, message);
        });
        
        log.warn("Method argument validation failed: {}", validationErrors);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Request validation failed")
                .path(extractPath(request))
                .code("VALIDATION_FAILED")
                .traceId(generateTraceId())
                .details(new HashMap<>(validationErrors))
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        
        Map<String, String> validationErrors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                    violation -> violation.getPropertyPath().toString(),
                    ConstraintViolation::getMessage
                ));
        
        log.warn("Constraint validation failed: {}", validationErrors);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Constraint Violation")
                .message("Request constraints violated")
                .path(extractPath(request))
                .code("CONSTRAINT_VIOLATION")
                .traceId(generateTraceId())
                .details(new HashMap<>(validationErrors))
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    // ========================================
    // HTTP-RELATED EXCEPTIONS
    // ========================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, WebRequest request) {
        
        String message = "Malformed JSON request";
        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException cause = (InvalidFormatException) ex.getCause();
            message = String.format("Invalid value for field '%s': %s", 
                cause.getPath().get(0).getFieldName(), cause.getValue());
        }
        
        log.warn("HTTP message not readable: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Malformed Request")
                .message(message)
                .path(extractPath(request))
                .code("MALFORMED_REQUEST")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, WebRequest request) {
        
        String supportedMethods = String.join(", ", ex.getSupportedMethods());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .error("Method Not Allowed")
                .message(String.format("Method %s not supported. Supported methods: %s", 
                    ex.getMethod(), supportedMethods))
                .path(extractPath(request))
                .code("METHOD_NOT_ALLOWED")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, WebRequest request) {
        
        String supportedTypes = ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                .error("Unsupported Media Type")
                .message(String.format("Media type %s not supported. Supported types: %s", 
                    ex.getContentType(), supportedTypes))
                .path(extractPath(request))
                .code("UNSUPPORTED_MEDIA_TYPE")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }

    // ========================================
    // DATABASE-RELATED EXCEPTIONS
    // ========================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {
        
        log.error("Data integrity violation: {}", ex.getMessage(), ex);
        
        String message = "Data integrity violation";
        String code = "DATA_INTEGRITY_VIOLATION";
        
        // Analyze specific constraint violations
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            String causeMessage = ex.getCause().getMessage().toLowerCase();
            if (causeMessage.contains("unique")) {
                message = "Duplicate entry detected";
                code = "DUPLICATE_ENTRY";
            } else if (causeMessage.contains("foreign key")) {
                message = "Referenced resource does not exist";
                code = "FOREIGN_KEY_VIOLATION";
            } else if (causeMessage.contains("not null")) {
                message = "Required field cannot be empty";
                code = "NULL_CONSTRAINT_VIOLATION";
            }
        }
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Data Integrity Error")
                .message(message)
                .path(extractPath(request))
                .code(code)
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex, WebRequest request) {
        
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Concurrent Modification")
                .message("Resource was modified by another process. Please refresh and try again.")
                .path(extractPath(request))
                .code("OPTIMISTIC_LOCK_FAILURE")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystem(
            TransactionSystemException ex, WebRequest request) {
        
        log.error("Transaction system error: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Transaction Error")
                .message("Transaction processing failed")
                .path(extractPath(request))
                .code("TRANSACTION_ERROR")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========================================
    // EXTERNAL SERVICE EXCEPTIONS
    // ========================================

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(
            ExternalServiceException ex, WebRequest request) {
        
        log.error("External service error: {} - Status: {}", ex.getMessage(), ex.getStatusCode(), ex);
        
        // Report to monitoring
        errorReportingService.reportExternalServiceError(ex);
        
        HttpStatus responseStatus = HttpStatus.valueOf(ex.getStatusCode());
        if (responseStatus.is5xxServerError()) {
            responseStatus = HttpStatus.BAD_GATEWAY;
        }
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(responseStatus.value())
                .error("External Service Error")
                .message("External service temporarily unavailable")
                .path(extractPath(request))
                .code("EXTERNAL_SERVICE_ERROR")
                .traceId(generateTraceId())
                .details(Map.of("service", ex.getServiceName()))
                .build();
        
        return ResponseEntity.status(responseStatus).body(response);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(
            TimeoutException ex, WebRequest request) {
        
        log.warn("Request timeout: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.REQUEST_TIMEOUT.value())
                .error("Request Timeout")
                .message("Request processing timed out")
                .path(extractPath(request))
                .code("REQUEST_TIMEOUT")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletionException(
            CompletionException ex, WebRequest request) {
        
        // Unwrap the actual cause
        Throwable cause = ex.getCause();
        if (cause instanceof RuntimeException) {
            return handleRuntimeException((RuntimeException) cause, request);
        }
        
        log.error("Completion exception: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Async Processing Error")
                .message("Asynchronous operation failed")
                .path(extractPath(request))
                .code("ASYNC_ERROR")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========================================
    // FILE AND IO EXCEPTIONS
    // ========================================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, WebRequest request) {
        
        log.warn("Upload size exceeded: {}", ex.getMaxUploadSize());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Upload Too Large")
                .message(String.format("Upload size exceeds maximum allowed size of %d bytes", 
                    ex.getMaxUploadSize()))
                .path(extractPath(request))
                .code("UPLOAD_TOO_LARGE")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFileAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        
        log.warn("File access denied: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error("File Access Denied")
                .message("Access to the requested file is denied")
                .path(extractPath(request))
                .code("FILE_ACCESS_DENIED")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(
            IOException ex, WebRequest request) {
        
        log.error("IO exception: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("IO Error")
                .message("Input/output operation failed")
                .path(extractPath(request))
                .code("IO_ERROR")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========================================
    // CRYPTOGRAPHIC EXCEPTIONS
    // ========================================

    @ExceptionHandler(BadPaddingException.class)
    public ResponseEntity<ErrorResponse> handleBadPadding(
            BadPaddingException ex, WebRequest request) {
        
        String clientIp = extractClientIp(request);
        log.error("SECURITY ALERT: Bad padding exception from IP {}: {}", clientIp, ex.getMessage());
        
        // This could indicate tampering attempts
        securityAuditService.reportCryptographicError(clientIp, "BAD_PADDING", extractUserAgent(request));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Cryptographic Error")
                .message("Invalid encrypted data format")
                .path(extractPath(request))
                .code("CRYPTO_ERROR")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    // ========================================
    // HSM-RELATED EXCEPTIONS
    // ========================================

    @ExceptionHandler(HSMException.class)
    public ResponseEntity<ErrorResponse> handleHSMException(
            HSMException ex, WebRequest request) {
        
        String clientIp = extractClientIp(request);
        log.error("SECURITY ALERT: HSM operation failed from IP {}: {}", clientIp, ex.getMessage(), ex);
        
        // Report HSM failures to security monitoring
        securityAuditService.reportSecurityViolation(
            clientIp, 
            "HSM_FAILURE",
            ex.getMessage(),
            extractUserAgent(request)
        );
        
        // Determine response status based on error type
        HttpStatus responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "Hardware security module operation failed";
        
        if (ex.getMessage().toLowerCase().contains("authentication")) {
            responseStatus = HttpStatus.UNAUTHORIZED;
            errorMessage = "HSM authentication failed";
        } else if (ex.getMessage().toLowerCase().contains("tamper")) {
            responseStatus = HttpStatus.FORBIDDEN;
            errorMessage = "HSM tamper detected - access denied";
        } else if (ex.getMessage().toLowerCase().contains("key not found")) {
            responseStatus = HttpStatus.NOT_FOUND;
            errorMessage = "Required cryptographic key not found";
        } else if (ex.getMessage().toLowerCase().contains("timeout")) {
            responseStatus = HttpStatus.REQUEST_TIMEOUT;
            errorMessage = "HSM operation timed out";
        }
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(responseStatus.value())
                .error("Hardware Security Module Error")
                .message(errorMessage)
                .path(extractPath(request))
                .code(ex.getErrorCode())
                .traceId(generateTraceId())
                .details(Map.of(
                    "hsmProvider", "PKCS11_GENERIC",
                    "securityLevel", "FIPS_140_2",
                    "remediationAction", "Contact security team immediately"
                ))
                .build();
        
        return ResponseEntity.status(responseStatus).body(response);
    }

    // ========================================
    // GENERIC EXCEPTIONS
    // ========================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        
        log.warn("Illegal argument: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Invalid Argument")
                .message(ex.getMessage())
                .path(extractPath(request))
                .code("ILLEGAL_ARGUMENT")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        
        log.warn("Illegal state: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Invalid State")
                .message(ex.getMessage())
                .path(extractPath(request))
                .code("ILLEGAL_STATE")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        
        // Report critical errors to monitoring
        errorReportingService.reportCriticalError(ex, extractPath(request), extractClientIp(request));
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred")
                .path(extractPath(request))
                .code("INTERNAL_ERROR")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handle generic runtime exceptions
     */
    private ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, WebRequest request) {
        log.error("Runtime exception occurred", ex);
        
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Runtime Error")
                .message("A runtime error occurred: " + ex.getMessage())
                .path(extractPath(request))
                .code("RUNTIME_ERROR")
                .traceId(generateTraceId())
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    private String extractClientIp(WebRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fallback to remote address header if available
        String remoteAddr = request.getHeader("Remote-Addr");
        if (remoteAddr != null && !remoteAddr.isEmpty()) {
            return remoteAddr;
        }
        
        // Last resort - return unknown
        return "unknown";
    }

    private String extractUserAgent(WebRequest request) {
        return request.getHeader("User-Agent");
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}