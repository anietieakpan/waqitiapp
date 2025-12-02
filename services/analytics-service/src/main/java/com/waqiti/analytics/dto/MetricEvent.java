package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Metric event for real-time processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricEvent {
    
    private UUID eventId;
    private String metricType; // e.g., TXN_VOLUME, API_RESPONSE_TIME, SYS_CPU_USAGE
    private BigDecimal value;
    private LocalDateTime timestamp;
    private String source;
    
    // Context
    private UUID userId;
    private UUID transactionId;
    private String accountId;
    private String sessionId;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    // Dimensions for grouping
    private Map<String, String> dimensions;
    
    @PrePersist
    protected void onCreate() {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
    
    /**
     * Helper method to create a simple metric event
     */
    public static MetricEvent of(String metricType, double value) {
        return MetricEvent.builder()
            .eventId(UUID.randomUUID())
            .metricType(metricType)
            .value(BigDecimal.valueOf(value))
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Helper method to create a metric event with metadata
     */
    public static MetricEvent of(String metricType, double value, Map<String, Object> metadata) {
        return MetricEvent.builder()
            .eventId(UUID.randomUUID())
            .metricType(metricType)
            .value(BigDecimal.valueOf(value))
            .timestamp(LocalDateTime.now())
            .metadata(metadata)
            .build();
    }
}