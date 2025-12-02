package com.waqiti.common.exception;

import com.waqiti.common.dto.error.ErrorResponse;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.UUID;

/**
 * Unified Global Exception Handler for Waqiti Platform
 * 
 * This replaces all individual service exception handlers with a single,
 * comprehensive implementation using the unified ErrorResponse.
 *
 * @author Waqiti Platform Team
 * @version 3.0.0
 * @since 2025-11-23
 */
@Slf4j
@RestControllerAdvice
public class WaqitiGlobalExceptionHandler {

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    @Value("${INSTANCE_ID:${random.uuid}}")
    private String instanceId;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired
    private Environment environment;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();

        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.validationError()
                .message("Validation failed for request")
                .userFriendlyMessage("Please check your input and try again")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .correlationId(correlationId);

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            builder.addFieldError(
                    fieldError.getField(),
                    fieldError.getDefaultMessage(),
                    fieldError.getRejectedValue()
            );
        }

        ErrorResponse response = enrichErrorResponse(builder).build();

        log.warn("Validation error: correlationId={}, path={}, errors={}",
                correlationId, request.getRequestURI(), ex.getBindingResult().getErrorCount());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();

        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.validationError()
                .message("Constraint violation")
                .userFriendlyMessage("Invalid input provided")
                .path(request.getRequestURI())
                .method(request.getMethod())
                .correlationId(correlationId);

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            builder.addFieldError(
                    violation.getPropertyPath().toString(),
                    violation.getMessage(),
                    violation.getInvalidValue()
            );
        }

        ErrorResponse response = enrichErrorResponse(builder).build();

        log.warn("Constraint violation: correlationId={}, path={}, violations={}",
                correlationId, request.getRequestURI(), ex.getConstraintViolations().size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();

        ErrorResponse response = enrichErrorResponse(ErrorResponse.authorizationError()
                .path(request.getRequestURI())
                .method(request.getMethod())
                .correlationId(correlationId)
        ).build();

        log.warn("Access denied: correlationId={}, path={}", correlationId, request.getRequestURI());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String correlationId = getCorrelationId();
        String supportReferenceId = generateSupportReferenceId();

        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.internalError()
                .path(request.getRequestURI())
                .method(request.getMethod())
                .correlationId(correlationId)
                .supportReferenceId(supportReferenceId);

        ErrorResponse response = enrichErrorResponse(builder).build();

        if (isDevelopmentEnvironment()) {
            response.setStackTrace(getStackTrace(ex));
        }

        log.error("Internal server error: correlationId={}, path={}, supportRef={}, error={}",
                correlationId, request.getRequestURI(), supportReferenceId, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private ErrorResponse.ErrorResponseBuilder enrichErrorResponse(ErrorResponse.ErrorResponseBuilder builder) {
        builder.service(serviceName);
        builder.instance(instanceId);

        if (tracer != null && tracer.currentSpan() != null) {
            builder.traceId(tracer.currentSpan().context().traceId());
            builder.spanId(tracer.currentSpan().context().spanId());
        }

        return builder;
    }

    private String getCorrelationId() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String correlationId = request.getHeader("X-Correlation-ID");
            return correlationId != null ? correlationId : UUID.randomUUID().toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String generateSupportReferenceId() {
        return String.format("SUP-%d-%s",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    private boolean isDevelopmentEnvironment() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test")
                || Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
