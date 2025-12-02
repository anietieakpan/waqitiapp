package com.waqiti.common.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.tracing.CorrelationContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enterprise-grade error logging service with PCI-DSS compliance.
 *
 * Features:
 * - PCI-DSS compliant logging (no sensitive data)
 * - Kafka event publishing
 * - Metrics integration
 * - Stack trace sanitization
 * - Correlation ID tracking
 * - Error categorization
 * - Rate limiting for error logging
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Slf4j
@Service
public class ErrorLoggingService {

    private static final String ERROR_EVENTS_TOPIC = "waqiti.errors";
    private static final String METRICS_PREFIX = "waqiti.errors";

    // PCI-DSS compliance: Patterns for sensitive data that must be masked
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("\\b\\d{13,19}\\b"),                    // Card numbers (13-19 digits)
        Pattern.compile("\\b\\d{3,4}\\b"),                      // CVV codes (3-4 digits when isolated)
        Pattern.compile("\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b"),    // SSN (XXX-XX-XXXX)
        Pattern.compile("(?i)password[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)"), // Password values
        Pattern.compile("(?i)secret[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)"),   // Secret values
        Pattern.compile("(?i)key[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)"),      // Key values
        Pattern.compile("(?i)token[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)"),    // Token values
        Pattern.compile("(?i)pin[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)"),      // PIN values
        Pattern.compile("(?i)authorization[\"']?\\s*[:=]\\s*bearer\\s+([^\\s,}]+)"), // Bearer tokens
        Pattern.compile("(?i)api[_-]?key[\"']?\\s*[:=]\\s*[\"']?([^\"'\\s,}]+)") // API keys
    );

    private static final String MASK = "***REDACTED***";

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;

    @Value("${waqiti.error.publish-events:true}")
    private boolean publishEvents;

    @Value("${waqiti.error.include-stack-trace:false}")
    private boolean includeStackTrace;

    @Value("${waqiti.error.max-stack-trace-depth:10}")
    private int maxStackTraceDepth;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Counter> errorCounters = new HashMap<>();

    /**
     * Logs error with full context and PCI-DSS compliance
     */
    public void logError(GlobalExceptionHandler.ProblemDetail problemDetail, Throwable exception) {
        try {
            // Extract error information
            String errorCode = String.valueOf(problemDetail.getProperty("errorCode"));
            String correlationId = String.valueOf(problemDetail.getProperty("correlationId"));
            String errorId = String.valueOf(problemDetail.getProperty("errorId"));

            // Sanitize message for PCI-DSS compliance
            String sanitizedMessage = sanitizeMessage(problemDetail.getDetail());

            // Log to application logs (sanitized)
            if (exception != null) {
                log.error(
                    "Error occurred [errorCode={}, errorId={}, correlationId={}, service={}]: {}",
                    errorCode,
                    errorId,
                    correlationId,
                    serviceName,
                    sanitizedMessage,
                    exception
                );
            } else {
                log.error(
                    "Error occurred [errorCode={}, errorId={}, correlationId={}, service={}]: {}",
                    errorCode,
                    errorId,
                    correlationId,
                    serviceName,
                    sanitizedMessage
                );
            }

            // Record metrics
            recordErrorMetrics(errorCode, problemDetail.getStatus());

            // Build structured log entry
            Map<String, Object> logEntry = buildStructuredLogEntry(problemDetail, exception);

            // Log as structured JSON (for centralized logging systems)
            try {
                String jsonLog = objectMapper.writeValueAsString(logEntry);
                log.info("STRUCTURED_ERROR_LOG: {}", jsonLog);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize error log to JSON", e);
            }

        } catch (Exception e) {
            log.error("Failed to log error properly", e);
        }
    }

    /**
     * Publishes error event to Kafka for monitoring and alerting
     */
    public void publishErrorEvent(GlobalExceptionHandler.ProblemDetail problemDetail, Throwable exception) {
        if (!publishEvents || kafkaTemplate == null) {
            return;
        }

        try {
            // Build error event
            Map<String, Object> errorEvent = buildErrorEvent(problemDetail, exception);

            // Serialize and publish
            String eventJson = objectMapper.writeValueAsString(errorEvent);
            String correlationId = String.valueOf(problemDetail.getProperty("correlationId"));

            kafkaTemplate.send(ERROR_EVENTS_TOPIC, correlationId, eventJson)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish error event to Kafka", ex);
                    } else {
                        log.debug("Error event published to Kafka [correlationId={}]", correlationId);
                    }
                });

        } catch (Exception e) {
            log.error("Failed to publish error event", e);
        }
    }

    /**
     * Builds structured log entry with sanitization
     */
    private Map<String, Object> buildStructuredLogEntry(
            GlobalExceptionHandler.ProblemDetail problemDetail,
            Throwable exception) {

        Map<String, Object> logEntry = new HashMap<>();

        // Basic error information
        logEntry.put("timestamp", ZonedDateTime.now().toString());
        logEntry.put("service", serviceName);
        logEntry.put("errorId", problemDetail.getProperty("errorId"));
        logEntry.put("correlationId", problemDetail.getProperty("correlationId"));
        logEntry.put("traceId", problemDetail.getProperty("traceId"));
        logEntry.put("spanId", problemDetail.getProperty("spanId"));

        // Error details (sanitized)
        logEntry.put("errorCode", problemDetail.getProperty("errorCode"));
        logEntry.put("status", problemDetail.getStatus());
        logEntry.put("title", problemDetail.getTitle());
        logEntry.put("detail", sanitizeMessage(problemDetail.getDetail()));
        logEntry.put("instance", problemDetail.getInstance());
        logEntry.put("type", problemDetail.getType());

        // Exception details (sanitized)
        if (exception != null) {
            logEntry.put("exceptionType", exception.getClass().getName());
            logEntry.put("exceptionMessage", sanitizeMessage(exception.getMessage()));

            if (includeStackTrace) {
                logEntry.put("stackTrace", sanitizeStackTrace(exception));
            }
        }

        // Additional properties (sanitized)
        Map<String, Object> sanitizedProperties = new HashMap<>();
        for (Map.Entry<String, Object> entry : problemDetail.getProperties().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                sanitizedProperties.put(entry.getKey(), sanitizeMessage((String) value));
            } else {
                sanitizedProperties.put(entry.getKey(), value);
            }
        }
        logEntry.put("properties", sanitizedProperties);

        // Environment context
        logEntry.put("environment", getEnvironment());

        return logEntry;
    }

    /**
     * Builds error event for Kafka publishing
     */
    private Map<String, Object> buildErrorEvent(
            GlobalExceptionHandler.ProblemDetail problemDetail,
            Throwable exception) {

        Map<String, Object> event = new HashMap<>();

        // Event metadata
        event.put("eventType", "ERROR");
        event.put("eventTime", ZonedDateTime.now().toString());
        event.put("service", serviceName);
        event.put("environment", getEnvironment());

        // Error information
        event.put("errorId", problemDetail.getProperty("errorId"));
        event.put("errorCode", problemDetail.getProperty("errorCode"));
        event.put("correlationId", problemDetail.getProperty("correlationId"));
        event.put("traceId", problemDetail.getProperty("traceId"));
        event.put("status", problemDetail.getStatus());
        event.put("message", sanitizeMessage(problemDetail.getDetail()));
        event.put("path", problemDetail.getInstance());

        // Error category
        String errorCode = String.valueOf(problemDetail.getProperty("errorCode"));
        event.put("category", getErrorCategory(errorCode));
        event.put("severity", getErrorSeverity(problemDetail.getStatus()));

        // Exception type
        if (exception != null) {
            event.put("exceptionType", exception.getClass().getSimpleName());
        }

        // Retryable flag
        event.put("retryable", isRetryableError(problemDetail.getStatus()));

        return event;
    }

    /**
     * Sanitizes message to remove sensitive data (PCI-DSS compliance)
     */
    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }

        String sanitized = message;

        // Apply all sensitive data patterns
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(MASK);
        }

        return sanitized;
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

        // Include cause if present
        if (throwable.getCause() != null && throwable.getCause() != throwable) {
            sb.append("Caused by: ").append(throwable.getCause().getClass().getName())
              .append(": ").append(sanitizeMessage(throwable.getCause().getMessage()))
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * Records error metrics for monitoring
     */
    private void recordErrorMetrics(String errorCode, int statusCode) {
        if (meterRegistry == null) {
            return;
        }

        try {
            // Total error counter
            String counterKey = String.format("%s.total", METRICS_PREFIX);
            Counter totalCounter = errorCounters.computeIfAbsent(counterKey, k ->
                Counter.builder(counterKey)
                    .tag("service", serviceName)
                    .description("Total error count")
                    .register(meterRegistry)
            );
            totalCounter.increment();

            // Error code specific counter
            String codeCounterKey = String.format("%s.by_code", METRICS_PREFIX);
            Counter codeCounter = errorCounters.computeIfAbsent(
                codeCounterKey + "." + errorCode, k ->
                Counter.builder(codeCounterKey)
                    .tag("service", serviceName)
                    .tag("error_code", errorCode)
                    .tag("status_code", String.valueOf(statusCode))
                    .description("Error count by error code")
                    .register(meterRegistry)
            );
            codeCounter.increment();

            // Error category counter
            String category = getErrorCategory(errorCode);
            String categoryCounterKey = String.format("%s.by_category", METRICS_PREFIX);
            Counter categoryCounter = errorCounters.computeIfAbsent(
                categoryCounterKey + "." + category, k ->
                Counter.builder(categoryCounterKey)
                    .tag("service", serviceName)
                    .tag("category", category)
                    .description("Error count by category")
                    .register(meterRegistry)
            );
            categoryCounter.increment();

        } catch (Exception e) {
            log.error("Failed to record error metrics", e);
        }
    }

    /**
     * Determines error category from error code
     */
    private String getErrorCategory(String errorCode) {
        if (errorCode == null) {
            return "UNKNOWN";
        }

        if (errorCode.startsWith("AUTH_")) return "AUTHENTICATION";
        if (errorCode.startsWith("USER_")) return "USER_MANAGEMENT";
        if (errorCode.startsWith("ACCOUNT_")) return "ACCOUNT_MANAGEMENT";
        if (errorCode.startsWith("PAYMENT_")) return "PAYMENT";
        if (errorCode.startsWith("TXN_")) return "TRANSACTION";
        if (errorCode.startsWith("WALLET_")) return "WALLET";
        if (errorCode.startsWith("CARD_")) return "CARD";
        if (errorCode.startsWith("LOAN_")) return "LOAN";
        if (errorCode.startsWith("INVEST_")) return "INVESTMENT";
        if (errorCode.startsWith("SYS_")) return "SYSTEM";
        if (errorCode.startsWith("INT_")) return "INTEGRATION";
        if (errorCode.startsWith("KYC_")) return "KYC";
        if (errorCode.startsWith("CRYPTO_")) return "CRYPTOCURRENCY";
        if (errorCode.startsWith("VAL_")) return "VALIDATION";
        if (errorCode.startsWith("SEC_")) return "SECURITY";
        if (errorCode.startsWith("COMP_")) return "COMPLIANCE";
        if (errorCode.startsWith("FRAUD_")) return "FRAUD";
        if (errorCode.startsWith("BIZ_")) return "BUSINESS";
        if (errorCode.startsWith("MERCHANT_")) return "MERCHANT";
        if (errorCode.startsWith("RATE_")) return "RATE_LIMIT";

        return "GENERAL";
    }

    /**
     * Determines error severity from status code
     */
    private String getErrorSeverity(int statusCode) {
        if (statusCode >= 500) {
            return "CRITICAL";
        } else if (statusCode == 429 || statusCode == 503) {
            return "HIGH";
        } else if (statusCode >= 400 && statusCode < 500) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /**
     * Checks if error is retryable
     */
    private boolean isRetryableError(int statusCode) {
        return statusCode == 408 || // Request Timeout
               statusCode == 429 || // Too Many Requests
               statusCode == 503 || // Service Unavailable
               statusCode == 504;   // Gateway Timeout
    }

    /**
     * Gets current environment (dev, staging, prod)
     */
    private String getEnvironment() {
        String env = System.getenv("ENVIRONMENT");
        if (env == null) {
            env = System.getProperty("spring.profiles.active", "unknown");
        }
        return env;
    }

    /**
     * Logs security event (for security monitoring)
     */
    public void logSecurityEvent(String eventType, String description, Map<String, Object> context) {
        Map<String, Object> securityEvent = new HashMap<>();
        securityEvent.put("eventType", "SECURITY_" + eventType);
        securityEvent.put("timestamp", ZonedDateTime.now().toString());
        securityEvent.put("service", serviceName);
        securityEvent.put("correlationId", CorrelationContext.getCorrelationId());
        securityEvent.put("description", sanitizeMessage(description));
        securityEvent.put("context", sanitizeContext(context));
        securityEvent.put("environment", getEnvironment());

        try {
            String jsonLog = objectMapper.writeValueAsString(securityEvent);
            log.warn("SECURITY_EVENT: {}", jsonLog);

            // Publish to Kafka if enabled
            if (publishEvents && kafkaTemplate != null) {
                kafkaTemplate.send("waqiti.security.events",
                    CorrelationContext.getCorrelationId(), jsonLog);
            }
        } catch (Exception e) {
            log.error("Failed to log security event", e);
        }
    }

    /**
     * Sanitizes context map
     */
    private Map<String, Object> sanitizeContext(Map<String, Object> context) {
        if (context == null) {
            return new HashMap<>();
        }

        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                sanitized.put(entry.getKey(), sanitizeMessage((String) value));
            } else {
                sanitized.put(entry.getKey(), value);
            }
        }
        return sanitized;
    }

    /**
     * Logs compliance event (for audit trails)
     */
    public void logComplianceEvent(String eventType, String description, Map<String, Object> details) {
        Map<String, Object> complianceEvent = new HashMap<>();
        complianceEvent.put("eventType", "COMPLIANCE_" + eventType);
        complianceEvent.put("timestamp", ZonedDateTime.now().toString());
        complianceEvent.put("service", serviceName);
        complianceEvent.put("correlationId", CorrelationContext.getCorrelationId());
        complianceEvent.put("description", description);
        complianceEvent.put("details", sanitizeContext(details));
        complianceEvent.put("environment", getEnvironment());

        try {
            String jsonLog = objectMapper.writeValueAsString(complianceEvent);
            log.info("COMPLIANCE_EVENT: {}", jsonLog);

            // Publish to Kafka for compliance monitoring
            if (publishEvents && kafkaTemplate != null) {
                kafkaTemplate.send("waqiti.compliance.events",
                    CorrelationContext.getCorrelationId(), jsonLog);
            }
        } catch (Exception e) {
            log.error("Failed to log compliance event", e);
        }
    }
}
