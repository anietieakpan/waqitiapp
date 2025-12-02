package com.waqiti.common.error;

import com.waqiti.common.tracing.CorrelationContext;
import com.waqiti.common.tracing.DistributedTracingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Global exception handler for all Waqiti microservices.
 *
 * Features:
 * - RFC 7807 Problem Details for HTTP APIs compliance
 * - Comprehensive error code mapping
 * - Correlation ID tracking
 * - Stack trace sanitization (PCI-DSS compliant)
 * - I18n support for error messages
 * - Metrics integration (error rate tracking)
 * - Distributed tracing integration
 * - Kafka error event publishing
 * - Retry-After headers for rate limiting
 * - Developer-friendly error messages
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String ERROR_DOCUMENTATION_BASE_URL = "https://api.example.com/errors/";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String ERROR_ID_HEADER = "X-Error-ID";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    // Sensitive patterns that should never appear in error messages (PCI-DSS compliance)
    private static final List<String> SENSITIVE_PATTERNS = Arrays.asList(
        "\\b\\d{13,19}\\b",  // Card numbers
        "\\b\\d{3,4}\\b",     // CVV codes
        "\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b", // SSN
        "(?i)password",
        "(?i)secret",
        "(?i)key",
        "(?i)token",
        "(?i)pin\\b"
    );

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;

    @Value("${waqiti.error.include-stack-trace:false}")
    private boolean includeStackTrace;

    @Value("${waqiti.error.include-binding-errors:true}")
    private boolean includeBindingErrors;

    @Value("${waqiti.error.max-stack-trace-depth:10}")
    private int maxStackTraceDepth;

    @Autowired(required = false)
    private MessageSource messageSource;

    @Autowired(required = false)
    private DistributedTracingService tracingService;

    @Autowired(required = false)
    private ErrorLoggingService errorLoggingService;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    // Error counters for metrics
    private final Map<String, Counter> errorCounters = new HashMap<>();

    /**
     * Handles all unhandled exceptions as a catch-all
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception occurred", ex);

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.SYS_INTERNAL_ERROR,
            "An unexpected error occurred. Please try again later.",
            request,
            ex
        );

        recordError(ErrorCode.SYS_INTERNAL_ERROR, ex);
        publishErrorEvent(problem, ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles BusinessException - domain/business logic errors
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        log.warn("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode());
        ErrorCode errorCode = ErrorCode.fromCode(ex.getErrorCode());

        ProblemDetail problem = buildProblemDetail(
            status,
            errorCode,
            ex.getMessage(),
            request,
            ex
        );

        recordError(errorCode, ex);

        return ResponseEntity
            .status(status)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles ResourceNotFoundException
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.NOT_FOUND,
            ErrorCode.fromCode(ex.getErrorCode()),
            ex.getMessage(),
            request,
            ex
        );

        // Add resource details
        if (ex.getResourceType() != null) {
            problem.setProperty("resourceType", ex.getResourceType());
        }
        if (ex.getResourceId() != null) {
            problem.setProperty("resourceId", sanitizeValue(ex.getResourceId()));
        }

        recordError(ErrorCode.fromCode(ex.getErrorCode()), ex);

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles ValidationException
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            ValidationException ex, HttpServletRequest request) {

        log.warn("Validation exception: {}", ex.getMessage());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            ex.getErrorCodeEnum(),
            "Validation failed",
            request,
            ex
        );

        Map<String, Object> validationErrors = new HashMap<>();
        validationErrors.put("message", ex.getMessage());
        validationErrors.put("errors", ex.getErrors());
        if (ex.getField() != null) {
            validationErrors.put("field", ex.getField());
        }

        problem.setProperty("validationErrors", validationErrors);

        recordError(ex.getErrorCodeEnum(), ex);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles ServiceUnavailableException
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleServiceUnavailableException(
            ServiceUnavailableException ex, HttpServletRequest request) {

        log.error("Service unavailable: {}", ex.getMessage(), ex);

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.getErrorCodeEnum(),
            ex.getMessage(),
            request,
            ex
        );

        if (ex.getRetryAfterSeconds() > 0) {
            problem.setProperty("retryAfter", ex.getRetryAfterSeconds());
        }

        if (ex.getServiceName() != null) {
            problem.setProperty("unavailableService", ex.getServiceName());
        }

        recordError(ex.getErrorCodeEnum(), ex);

        HttpHeaders headers = buildHeaders(problem);
        if (ex.getRetryAfterSeconds() > 0) {
            headers.add(RETRY_AFTER_HEADER, String.valueOf(ex.getRetryAfterSeconds()));
        }

        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .headers(headers)
            .body(problem);
    }

    /**
     * Handles Spring validation errors (JSR-303)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        log.warn("Method argument validation failed");

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_FAILED,
            "Request validation failed",
            request,
            ex
        );

        if (includeBindingErrors) {
            Map<String, List<String>> fieldErrors = new HashMap<>();

            ex.getBindingResult().getAllErrors().forEach(error -> {
                String fieldName = error instanceof FieldError
                    ? ((FieldError) error).getField()
                    : error.getObjectName();
                String errorMessage = error.getDefaultMessage();

                fieldErrors.computeIfAbsent(fieldName, k -> new ArrayList<>())
                    .add(errorMessage);
            });

            problem.setProperty("fieldErrors", fieldErrors);
            problem.setProperty("errorCount", fieldErrors.size());
        }

        recordError(ErrorCode.VALIDATION_FAILED, ex);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles constraint violation exceptions
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        log.warn("Constraint violation: {}", ex.getMessage());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VALIDATION_FAILED,
            "Constraint validation failed",
            request,
            ex
        );

        Map<String, String> violations = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                violation -> violation.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (v1, v2) -> v1 + "; " + v2
            ));

        problem.setProperty("violations", violations);

        recordError(ErrorCode.VALIDATION_FAILED, ex);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles authentication exceptions
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed: {}", ex.getMessage());

        ErrorCode errorCode = ex instanceof BadCredentialsException
            ? ErrorCode.AUTH_INVALID_CREDENTIALS
            : ErrorCode.AUTH_TOKEN_INVALID;

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.UNAUTHORIZED,
            errorCode,
            "Authentication failed",
            request,
            ex
        );

        recordError(errorCode, ex);

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles authorization exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.FORBIDDEN,
            ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS,
            "You don't have permission to access this resource",
            request,
            ex
        );

        recordError(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, ex);

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles database exceptions
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccessException(
            DataAccessException ex, HttpServletRequest request) {

        log.error("Database error occurred", ex);

        ErrorCode errorCode = ex instanceof DataIntegrityViolationException
            ? ErrorCode.VAL_PATTERN_MISMATCH
            : ErrorCode.SYS_DATABASE_ERROR;

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode,
            "A database error occurred. Please try again.",
            request,
            ex
        );

        recordError(errorCode, ex);
        publishErrorEvent(problem, ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles HTTP message not readable exceptions
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body: {}", ex.getMessage());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VAL_INVALID_FORMAT,
            "Malformed request body. Please check your JSON syntax.",
            request,
            ex
        );

        recordError(ErrorCode.VAL_INVALID_FORMAT, ex);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        log.warn("Missing request parameter: {}", ex.getParameterName());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VAL_REQUIRED_FIELD,
            String.format("Required parameter '%s' is missing", ex.getParameterName()),
            request,
            ex
        );

        problem.setProperty("missingParameter", ex.getParameterName());
        problem.setProperty("parameterType", ex.getParameterType());

        recordError(ErrorCode.VAL_REQUIRED_FIELD, ex);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        log.warn("Method argument type mismatch: {}", ex.getName());

        String expectedType = ex.getRequiredType() != null
            ? ex.getRequiredType().getSimpleName()
            : "unknown";

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            ErrorCode.VAL_INVALID_FORMAT,
            String.format("Invalid value for parameter '%s'. Expected type: %s",
                ex.getName(), expectedType),
            request,
            ex
        );

        problem.setProperty("parameter", ex.getName());
        problem.setProperty("expectedType", expectedType);
        problem.setProperty("providedValue", sanitizeValue(String.valueOf(ex.getValue())));

        recordError(ErrorCode.VAL_INVALID_FORMAT, ex);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles HTTP request method not supported
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        log.warn("HTTP method not supported: {}", ex.getMethod());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.METHOD_NOT_ALLOWED,
            ErrorCode.VAL_INVALID_FORMAT,
            String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod()),
            request,
            ex
        );

        if (ex.getSupportedHttpMethods() != null) {
            problem.setProperty("supportedMethods",
                ex.getSupportedHttpMethods().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));
        }

        recordError(ErrorCode.VAL_INVALID_FORMAT, ex);

        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles HTTP media type not supported
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

        log.warn("Media type not supported: {}", ex.getContentType());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            ErrorCode.VAL_INVALID_FORMAT,
            "Unsupported media type. Please use application/json.",
            request,
            ex
        );

        problem.setProperty("supportedMediaTypes",
            ex.getSupportedMediaTypes().stream()
                .map(Object::toString)
                .collect(Collectors.toList()));

        recordError(ErrorCode.VAL_INVALID_FORMAT, ex);

        return ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Handles no handler found exceptions (404)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {

        log.warn("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());

        ProblemDetail problem = buildProblemDetail(
            HttpStatus.NOT_FOUND,
            ErrorCode.fromCode("RESOURCE_NOT_FOUND"),
            String.format("No endpoint found for %s %s", ex.getHttpMethod(), ex.getRequestURL()),
            request,
            ex
        );

        recordError(ErrorCode.fromCode("RESOURCE_NOT_FOUND"), ex);

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .headers(buildHeaders(problem))
            .body(problem);
    }

    /**
     * Builds RFC 7807 compliant problem detail
     */
    private ProblemDetail buildProblemDetail(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            Throwable exception) {

        String correlationId = CorrelationContext.getCorrelationId();
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            CorrelationContext.setCorrelationId(correlationId);
        }

        String errorId = UUID.randomUUID().toString();
        String path = request.getRequestURI();

        // Get localized message if available
        String localizedMessage = message;
        if (messageSource != null) {
            try {
                localizedMessage = messageSource.getMessage(
                    errorCode.getCode(),
                    null,
                    message,
                    LocaleContextHolder.getLocale()
                );
            } catch (Exception e) {
                log.debug("No localized message found for error code: {}", errorCode.getCode());
            }
        }

        ProblemDetail problem = new ProblemDetail();
        problem.setType(ERROR_DOCUMENTATION_BASE_URL + errorCode.getCode().toLowerCase());
        problem.setTitle(status.getReasonPhrase());
        problem.setStatus(status.value());
        problem.setDetail(sanitizeMessage(localizedMessage));
        problem.setInstance(path);

        // Add standard properties
        problem.setProperty("errorCode", errorCode.getCode());
        problem.setProperty("errorId", errorId);
        problem.setProperty("correlationId", correlationId);
        problem.setProperty("timestamp", ZonedDateTime.now().toString());
        problem.setProperty("service", serviceName);

        // Add trace information if available
        if (tracingService != null) {
            String traceId = tracingService.getCurrentTraceId();
            String spanId = tracingService.getCurrentSpanId();

            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }
            if (spanId != null) {
                problem.setProperty("spanId", spanId);
            }

            // Record error in trace
            tracingService.recordError(exception);
        }

        // Add stack trace if enabled (only in non-production)
        if (includeStackTrace && exception != null) {
            problem.setProperty("stackTrace", sanitizeStackTrace(exception));
        }

        // Add suggestions for common errors
        String[] suggestions = getSuggestionsForError(errorCode);
        if (suggestions.length > 0) {
            problem.setProperty("suggestions", suggestions);
        }

        // Log error event
        if (errorLoggingService != null) {
            errorLoggingService.logError(problem, exception);
        }

        return problem;
    }

    /**
     * Builds response headers with correlation and trace IDs
     */
    private HttpHeaders buildHeaders(ProblemDetail problem) {
        HttpHeaders headers = new HttpHeaders();

        Object correlationId = problem.getProperty("correlationId");
        if (correlationId != null) {
            headers.add(CORRELATION_ID_HEADER, correlationId.toString());
        }

        Object traceId = problem.getProperty("traceId");
        if (traceId != null) {
            headers.add(TRACE_ID_HEADER, traceId.toString());
        }

        Object errorId = problem.getProperty("errorId");
        if (errorId != null) {
            headers.add(ERROR_ID_HEADER, errorId.toString());
        }

        return headers;
    }

    /**
     * Sanitizes error messages to remove sensitive data (PCI-DSS compliance)
     */
    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }

        String sanitized = message;
        for (String pattern : SENSITIVE_PATTERNS) {
            sanitized = sanitized.replaceAll(pattern, "***");
        }

        return sanitized;
    }

    /**
     * Sanitizes any value to remove sensitive data
     */
    private String sanitizeValue(String value) {
        if (value == null) {
            return null;
        }

        return sanitizeMessage(value);
    }

    /**
     * Sanitizes stack trace to remove sensitive information
     */
    private String sanitizeStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getName()).append(": ")
          .append(sanitizeMessage(throwable.getMessage())).append("\n");

        StackTraceElement[] elements = throwable.getStackTrace();
        int depth = Math.min(elements.length, maxStackTraceDepth);

        for (int i = 0; i < depth; i++) {
            sb.append("\tat ").append(elements[i].toString()).append("\n");
        }

        if (elements.length > maxStackTraceDepth) {
            sb.append("\t... ").append(elements.length - maxStackTraceDepth)
              .append(" more\n");
        }

        return sb.toString();
    }

    /**
     * Provides user-friendly suggestions for common errors
     */
    private String[] getSuggestionsForError(ErrorCode errorCode) {
        return switch (errorCode) {
            case AUTH_INVALID_CREDENTIALS -> new String[]{
                "Verify your username and password",
                "Check if your account is active",
                "Reset your password if you've forgotten it"
            };
            case AUTH_TOKEN_EXPIRED -> new String[]{
                "Log in again to refresh your session",
                "Check your system clock is synchronized"
            };
            case VALIDATION_FAILED -> new String[]{
                "Check the request format matches the API documentation",
                "Verify all required fields are provided",
                "Ensure field values are within acceptable ranges"
            };
            case RATE_LIMIT_EXCEEDED -> new String[]{
                "Wait before making another request",
                "Check the Retry-After header for wait time",
                "Consider implementing exponential backoff"
            };
            case SYS_DATABASE_ERROR -> new String[]{
                "Try again in a few moments",
                "Contact support if the issue persists"
            };
            default -> new String[]{};
        };
    }

    /**
     * Records error metrics
     */
    private void recordError(ErrorCode errorCode, Throwable exception) {
        if (meterRegistry == null) {
            return;
        }

        try {
            String errorCodeStr = errorCode.getCode();
            Counter counter = errorCounters.computeIfAbsent(errorCodeStr, code ->
                Counter.builder("waqiti.errors")
                    .tag("service", serviceName)
                    .tag("error_code", code)
                    .tag("error_type", exception != null ? exception.getClass().getSimpleName() : "Unknown")
                    .description("Count of errors by error code")
                    .register(meterRegistry)
            );

            counter.increment();
        } catch (Exception e) {
            log.error("Failed to record error metric", e);
        }
    }

    /**
     * Publishes error event to Kafka for monitoring
     */
    private void publishErrorEvent(ProblemDetail problem, Throwable exception) {
        if (errorLoggingService != null) {
            try {
                errorLoggingService.publishErrorEvent(problem, exception);
            } catch (Exception e) {
                log.error("Failed to publish error event", e);
            }
        }
    }

    /**
     * RFC 7807 Problem Detail representation
     */
    @lombok.Data
    public static class ProblemDetail {
        private String type;
        private String title;
        private int status;
        private String detail;
        private String instance;
        private Map<String, Object> properties = new HashMap<>();

        public void setProperty(String key, Object value) {
            properties.put(key, value);
        }

        public Object getProperty(String key) {
            return properties.get(key);
        }
    }
}
