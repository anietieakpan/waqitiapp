package com.waqiti.common.tracing;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Utility class for managing correlation IDs across distributed services.
 * Correlation IDs are used to trace requests across microservices and provide
 * end-to-end visibility in distributed systems.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
public final class CorrelationId {
    
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    public static final String SPAN_ID_HEADER = "X-Span-ID";
    public static final String PARENT_SPAN_ID_HEADER = "X-Parent-Span-ID";
    
    private static final String CORRELATION_ID_PREFIX = "wqt";
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile("^wqt-[a-f0-9]{32}$");
    private static final int CORRELATION_ID_LENGTH = 36; // "wqt-" + 32 hex characters
    
    private CorrelationId() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Generates a new correlation ID with the Waqiti prefix.
     * Format: wqt-{32 character hex string}
     *
     * @return a new correlation ID
     */
    @NonNull
    public static String generate() {
        return CORRELATION_ID_PREFIX + "-" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Validates if the provided string is a valid correlation ID.
     *
     * @param correlationId the correlation ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(@Nullable String correlationId) {
        if (correlationId == null || correlationId.length() != CORRELATION_ID_LENGTH) {
            return false;
        }
        return CORRELATION_ID_PATTERN.matcher(correlationId).matches();
    }
    
    /**
     * Extracts the UUID portion from a correlation ID.
     *
     * @param correlationId the correlation ID
     * @return the UUID portion or null if invalid
     */
    @Nullable
    public static String extractUuid(@Nullable String correlationId) {
        if (!isValid(correlationId)) {
            return null;
        }
        return correlationId.substring(4); // Remove "wqt-" prefix
    }
    
    /**
     * Creates a child correlation ID based on the parent ID.
     * This maintains the relationship between parent and child requests.
     *
     * @param parentId the parent correlation ID
     * @return a new child correlation ID
     */
    @NonNull
    public static String generateChild(@Nullable String parentId) {
        // For now, generate a new ID - can be enhanced to show parent-child relationship
        return generate();
    }
    
    /**
     * Sanitizes a correlation ID for logging purposes to prevent log injection.
     *
     * @param correlationId the correlation ID to sanitize
     * @return sanitized correlation ID
     */
    @NonNull
    public static String sanitizeForLogging(@Nullable String correlationId) {
        if (correlationId == null) {
            return "null";
        }
        
        if (!isValid(correlationId)) {
            return "invalid-" + correlationId.replaceAll("[^a-zA-Z0-9-]", "_");
        }
        
        return correlationId;
    }
    
    /**
     * Creates a short version of the correlation ID for compact logging.
     * Takes the first 8 characters after the prefix.
     *
     * @param correlationId the correlation ID
     * @return short version of the ID
     */
    @NonNull
    public static String toShortForm(@Nullable String correlationId) {
        if (!isValid(correlationId)) {
            return "invalid";
        }
        return correlationId.substring(0, 12); // "wqt-" + first 8 characters
    }
}