package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class Alert {
    private final Long id;
    private final AlertType type;
    private final AlertSeverity severity;
    private final String title;
    private final String message;
    private final String source;
    private final BigDecimal threshold;
    private final BigDecimal currentValue;
    private final LocalDateTime createdAt;
    private final LocalDateTime resolvedAt;
    private final AlertStatus status;
    private final String assignedTo;
    
    public enum AlertType {
        RECONCILIATION_FAILURE,
        PERFORMANCE_DEGRADATION,
        THRESHOLD_BREACH,
        SYSTEM_ERROR,
        SECURITY_ALERT,
        DATA_QUALITY
    }
    
    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum AlertStatus {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED,
        SUPPRESSED
    }
    
    public boolean isActive() {
        return status == AlertStatus.ACTIVE;
    }
    
    public boolean requiresImmedateAttention() {
        return severity == AlertSeverity.CRITICAL && isActive();
    }
    
    public Long getAgeInMinutes() {
        if (createdAt == null) {
            return 0L;
        }
        return java.time.Duration.between(createdAt, 
            resolvedAt != null ? resolvedAt : LocalDateTime.now()).toMinutes();
    }
}