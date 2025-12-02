package com.waqiti.common.dlq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Analyzes DLQ messages to categorize errors and identify root causes.
 * Provides intelligent error classification for automated handling decisions.
 */
@Component
@Slf4j
public class DlqErrorAnalyzer {

    private final MeterRegistry meterRegistry;

    // Error frequency tracking
    private final ConcurrentHashMap<String, Integer> errorFrequency = new ConcurrentHashMap<>();
    private static final int HIGH_FREQUENCY_THRESHOLD = 10;

    // Error pattern matchers
    private static final Pattern SERIALIZATION_PATTERN = Pattern.compile(
        "(?i)(serialization|deserialization|json|xml|parse|unmarshall|marshall)"
    );

    private static final Pattern VALIDATION_PATTERN = Pattern.compile(
        "(?i)(validation|constraint|required|invalid|missing|null|empty)"
    );

    private static final Pattern NETWORK_PATTERN = Pattern.compile(
        "(?i)(connection|network|timeout|unreachable|refused|reset|socket)"
    );

    private static final Pattern DATABASE_PATTERN = Pattern.compile(
        "(?i)(database|sql|hibernate|jpa|connection pool|deadlock|constraint violation)"
    );

    private static final Pattern AUTHENTICATION_PATTERN = Pattern.compile(
        "(?i)(authentication|unauthorized|invalid token|expired|credentials)"
    );

    private static final Pattern AUTHORIZATION_PATTERN = Pattern.compile(
        "(?i)(authorization|forbidden|access denied|permission|privilege)"
    );

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile(
        "(?i)(timeout|timed out|read timeout|write timeout|operation timeout)"
    );

    private static final Pattern EXTERNAL_SERVICE_PATTERN = Pattern.compile(
        "(?i)(external service|third party|api|service unavailable|502|503|504)"
    );

    // Transient error patterns
    private static final Pattern TRANSIENT_DB_PATTERN = Pattern.compile(
        "(?i)(temporary|transient|deadlock|lock timeout|connection pool|too many connections)"
    );

    public DlqErrorAnalyzer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Analyzes a DLQ message to determine error category and root cause.
     */
    public void analyzeError(DlqMessage dlqMessage) {
        String errorMessage = dlqMessage.getErrorMessage();
        String stackTrace = dlqMessage.getStackTrace();
        String fullErrorText = (errorMessage + " " + (stackTrace != null ? stackTrace : "")).toLowerCase();

        // Categorize the error
        DlqMessage.ErrorCategory category = categorizeError(fullErrorText);
        dlqMessage.setErrorCategory(category);

        // Identify root cause
        String rootCause = identifyRootCause(fullErrorText, category);
        dlqMessage.setRootCause(rootCause);

        // Determine error type for frequency tracking
        String errorType = generateErrorType(category, rootCause);
        dlqMessage.setErrorType(errorType);

        // Track error frequency
        trackErrorFrequency(dlqMessage.getOriginalTopic(), errorType);

        // Set additional metadata based on analysis
        setAdditionalMetadata(dlqMessage, fullErrorText);

        log.info("DLQ error analysis completed: messageId={}, category={}, rootCause={}, errorType={}",
            dlqMessage.getMessageId(), category, rootCause, errorType);
    }

    private DlqMessage.ErrorCategory categorizeError(String fullErrorText) {
        if (SERIALIZATION_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.SERIALIZATION_ERROR;
        }
        if (VALIDATION_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.VALIDATION_ERROR;
        }
        if (AUTHENTICATION_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.AUTHENTICATION_ERROR;
        }
        if (AUTHORIZATION_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.AUTHORIZATION_ERROR;
        }
        if (TIMEOUT_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.TIMEOUT_ERROR;
        }
        if (NETWORK_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.NETWORK_ERROR;
        }
        if (DATABASE_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.DATABASE_ERROR;
        }
        if (EXTERNAL_SERVICE_PATTERN.matcher(fullErrorText).find()) {
            return DlqMessage.ErrorCategory.EXTERNAL_SERVICE_ERROR;
        }
        if (fullErrorText.contains("configuration") || fullErrorText.contains("property")) {
            return DlqMessage.ErrorCategory.CONFIGURATION_ERROR;
        }

        return DlqMessage.ErrorCategory.UNKNOWN_ERROR;
    }

    private String identifyRootCause(String fullErrorText, DlqMessage.ErrorCategory category) {
        switch (category) {
            case SERIALIZATION_ERROR:
                if (fullErrorText.contains("json")) return "JSON_PARSING_ERROR";
                if (fullErrorText.contains("xml")) return "XML_PARSING_ERROR";
                if (fullErrorText.contains("deserialize")) return "DESERIALIZATION_ERROR";
                return "MESSAGE_FORMAT_ERROR";

            case VALIDATION_ERROR:
                if (fullErrorText.contains("required")) return "MISSING_REQUIRED_FIELD";
                if (fullErrorText.contains("constraint")) return "CONSTRAINT_VIOLATION";
                if (fullErrorText.contains("null")) return "NULL_VALUE_ERROR";
                return "VALIDATION_RULE_VIOLATION";

            case NETWORK_ERROR:
                if (fullErrorText.contains("connection refused")) return "CONNECTION_REFUSED";
                if (fullErrorText.contains("unreachable")) return "HOST_UNREACHABLE";
                if (fullErrorText.contains("reset")) return "CONNECTION_RESET";
                return "NETWORK_CONNECTIVITY_ISSUE";

            case DATABASE_ERROR:
                if (fullErrorText.contains("deadlock")) return "DATABASE_DEADLOCK";
                if (fullErrorText.contains("connection pool")) return "CONNECTION_POOL_EXHAUSTED";
                if (fullErrorText.contains("constraint violation")) return "DATABASE_CONSTRAINT_VIOLATION";
                if (fullErrorText.contains("sql")) return "SQL_EXECUTION_ERROR";
                return "DATABASE_OPERATION_FAILED";

            case TIMEOUT_ERROR:
                if (fullErrorText.contains("read timeout")) return "READ_TIMEOUT";
                if (fullErrorText.contains("write timeout")) return "WRITE_TIMEOUT";
                if (fullErrorText.contains("operation timeout")) return "OPERATION_TIMEOUT";
                return "PROCESSING_TIMEOUT";

            case AUTHENTICATION_ERROR:
                if (fullErrorText.contains("expired")) return "TOKEN_EXPIRED";
                if (fullErrorText.contains("invalid token")) return "INVALID_TOKEN";
                if (fullErrorText.contains("credentials")) return "INVALID_CREDENTIALS";
                return "AUTHENTICATION_FAILED";

            case AUTHORIZATION_ERROR:
                if (fullErrorText.contains("permission")) return "INSUFFICIENT_PERMISSIONS";
                if (fullErrorText.contains("access denied")) return "ACCESS_DENIED";
                return "AUTHORIZATION_FAILED";

            case EXTERNAL_SERVICE_ERROR:
                if (fullErrorText.contains("502")) return "BAD_GATEWAY";
                if (fullErrorText.contains("503")) return "SERVICE_UNAVAILABLE";
                if (fullErrorText.contains("504")) return "GATEWAY_TIMEOUT";
                return "EXTERNAL_SERVICE_FAILURE";

            case CONFIGURATION_ERROR:
                if (fullErrorText.contains("property")) return "MISSING_CONFIGURATION_PROPERTY";
                if (fullErrorText.contains("profile")) return "INVALID_PROFILE_CONFIGURATION";
                return "CONFIGURATION_ISSUE";

            default:
                return "UNKNOWN_ROOT_CAUSE";
        }
    }

    private String generateErrorType(DlqMessage.ErrorCategory category, String rootCause) {
        return category.toString() + ":" + rootCause;
    }

    private void trackErrorFrequency(String topic, String errorType) {
        String key = topic + ":" + errorType;
        int count = errorFrequency.merge(key, 1, Integer::sum);

        // Record metrics
        Counter.builder("dlq_error_frequency")
            .tag("topic", topic)
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment();

        if (count >= HIGH_FREQUENCY_THRESHOLD) {
            log.warn("High frequency error detected: topic={}, errorType={}, count={}",
                topic, errorType, count);
        }
    }

    private void setAdditionalMetadata(DlqMessage dlqMessage, String fullErrorText) {
        Map<String, Object> metadata = new ConcurrentHashMap<>();

        // Add retry recommendations
        metadata.put("retryRecommended", isRetryRecommended(dlqMessage.getErrorCategory()));

        // Add urgency level
        metadata.put("urgencyLevel", calculateUrgencyLevel(dlqMessage.getErrorCategory()));

        // Add expected resolution time
        metadata.put("expectedResolutionTime", getExpectedResolutionTime(dlqMessage.getErrorCategory()));

        // Add troubleshooting hints
        metadata.put("troubleshootingHints", getTroubleshootingHints(dlqMessage.getErrorCategory(), dlqMessage.getRootCause()));

        // Check for known issues
        metadata.put("knownIssue", checkForKnownIssues(fullErrorText));

        dlqMessage.setAdditionalMetadata(metadata);
    }

    public boolean isHighFrequencyError(String topic, String errorType) {
        String key = topic + ":" + errorType;
        Integer count = errorFrequency.get(key);
        return count != null && count >= HIGH_FREQUENCY_THRESHOLD;
    }

    public boolean isTransientDatabaseError(String errorMessage) {
        return TRANSIENT_DB_PATTERN.matcher(errorMessage.toLowerCase()).find();
    }

    private boolean isRetryRecommended(DlqMessage.ErrorCategory category) {
        switch (category) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
            case EXTERNAL_SERVICE_ERROR:
                return true;
            case DATABASE_ERROR:
                return true; // Will be further analyzed for transient vs permanent
            default:
                return false;
        }
    }

    private String calculateUrgencyLevel(DlqMessage.ErrorCategory category) {
        switch (category) {
            case AUTHENTICATION_ERROR:
            case AUTHORIZATION_ERROR:
                return "HIGH";
            case DATABASE_ERROR:
            case EXTERNAL_SERVICE_ERROR:
                return "MEDIUM";
            case SERIALIZATION_ERROR:
            case VALIDATION_ERROR:
                return "LOW";
            default:
                return "MEDIUM";
        }
    }

    private String getExpectedResolutionTime(DlqMessage.ErrorCategory category) {
        switch (category) {
            case NETWORK_ERROR:
            case TIMEOUT_ERROR:
                return "5-15 minutes";
            case DATABASE_ERROR:
                return "15-30 minutes";
            case EXTERNAL_SERVICE_ERROR:
                return "30-60 minutes";
            case SERIALIZATION_ERROR:
            case VALIDATION_ERROR:
                return "1-4 hours";
            case CONFIGURATION_ERROR:
                return "2-8 hours";
            default:
                return "4-24 hours";
        }
    }

    private String getTroubleshootingHints(DlqMessage.ErrorCategory category, String rootCause) {
        switch (category) {
            case NETWORK_ERROR:
                return "Check network connectivity, firewall rules, and service endpoints";
            case DATABASE_ERROR:
                return "Check database status, connection pools, and query performance";
            case SERIALIZATION_ERROR:
                return "Validate message format, schema compatibility, and data types";
            case VALIDATION_ERROR:
                return "Review business rules, input validation, and required fields";
            case AUTHENTICATION_ERROR:
                return "Check token validity, authentication service status, and credentials";
            case AUTHORIZATION_ERROR:
                return "Review user permissions, role assignments, and access policies";
            case TIMEOUT_ERROR:
                return "Check processing performance, resource utilization, and timeout configurations";
            case EXTERNAL_SERVICE_ERROR:
                return "Verify external service status, API endpoints, and rate limiting";
            case CONFIGURATION_ERROR:
                return "Review application configuration, environment variables, and properties";
            default:
                return "Analyze logs, check system resources, and review recent changes";
        }
    }

    private boolean checkForKnownIssues(String fullErrorText) {
        // This would typically check against a database of known issues
        // For now, return false but this could be enhanced with ML models
        return false;
    }
}