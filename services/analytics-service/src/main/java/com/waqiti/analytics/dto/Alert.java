package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private AlertType type;
    private Severity severity;
    private String message;
    private UUID userId;
    private UUID transactionId;
    private UUID entityId;
    private String entityType;
    private Map<String, Object> metrics;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    
    public enum AlertType {
        VOLUME_SPIKE, 
        HIGH_ERROR_RATE, 
        SLOW_RESPONSE, 
        USER_VELOCITY, 
        LARGE_TRANSACTION,
        FRAUD_DETECTED,
        SYSTEM_ANOMALY,
        PERFORMANCE_DEGRADATION,
        SECURITY_THREAT,
        COMPLIANCE_VIOLATION
    }
    
    public enum Severity {
        LOW, 
        MEDIUM, 
        HIGH, 
        CRITICAL
    }
}