package com.waqiti.common.observability;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Configuration for log correlation across distributed services
 * Manages trace IDs, correlation IDs, and structured logging
 */
@Data
@Builder
public class LogCorrelationConfig {
    
    private boolean enabled;
    private String correlationIdHeader;
    private String traceIdHeader;
    private boolean includeRequestHeaders;
    private boolean includeResponseHeaders;
    private List<String> sensitiveHeaders;
    private int maxFieldLength;
    private boolean enableStructuredLogging;
    private boolean includeTraceId;
    private boolean includeSpanId;
    private boolean includeUserId;
    private boolean includeSessionId;
    
    /**
     * Default configuration for log correlation
     */
    public static LogCorrelationConfig defaultConfig() {
        return LogCorrelationConfig.builder()
            .enabled(true)
            .correlationIdHeader("X-Correlation-ID")
            .traceIdHeader("X-Trace-ID")
            .includeRequestHeaders(true)
            .includeResponseHeaders(false)
            .sensitiveHeaders(List.of(
                "authorization", "x-api-key", "cookie", 
                "x-auth-token", "authentication"
            ))
            .maxFieldLength(1000)
            .enableStructuredLogging(true)
            .includeTraceId(true)
            .includeSpanId(true)
            .includeUserId(true)
            .includeSessionId(true)
            .build();
    }
    
    /**
     * Check if a header is sensitive and should be masked
     */
    public boolean isSensitiveHeader(String headerName) {
        if (sensitiveHeaders == null || headerName == null) {
            return false;
        }
        return sensitiveHeaders.stream()
            .anyMatch(sensitive -> headerName.toLowerCase().contains(sensitive.toLowerCase()));
    }
    
    /**
     * Mask sensitive field value
     */
    public String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}