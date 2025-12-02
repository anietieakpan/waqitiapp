package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Security event data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityEvent implements DomainEvent {
    private String eventType;
    private String userId;
    private String details;
    private long timestamp;
    private String severity;
    private String clientIp;
    private String endpoint;
    private String method;
    private String userAgent;

    // DomainEvent interface implementations
    @Override
    public String getEventId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timestamp);
    }

    @Override
    public String getTopic() {
        return "security-events";
    }

    @Override
    public String getAggregateId() {
        return userId;
    }

    @Override
    public String getAggregateType() {
        return "SecurityAggregate";
    }

    @Override
    public String getAggregateName() {
        return "Security Event";
    }

    @Override
    public Long getVersion() {
        return 1L;
    }

    @Override
    public String getCorrelationId() {
        return getEventId();
    }

    @Override
    public String getSourceService() {
        return "security-service";
    }

    @Override
    public Map<String, Object> getMetadata() {
        return Map.of(
            "severity", severity,
            "clientIp", clientIp,
            "endpoint", endpoint != null ? endpoint : "",
            "method", method != null ? method : "",
            "userAgent", userAgent != null ? userAgent : ""
        );
    }
}