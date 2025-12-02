package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.waqiti.common.enums.RiskLevel;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud detection event for publishing to event stream
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FraudEvent extends FinancialEvent implements Serializable {
    
    private String transactionId;
    private FraudLevel fraudLevel;
    private Double riskScore;
    private BigDecimal transactionAmount;
    private String currency;
    private String description;
    private LocalDateTime detectedAt;
    private Map<String, Object> metadata;
    private String detectionMethod;
    private boolean actionTaken;
    private String actionType;
    
    public enum EventType {
        FRAUD_DETECTED,
        FRAUD_SUSPECTED,
        FRAUD_CLEARED,
        MANUAL_REVIEW_REQUIRED,
        PATTERN_DETECTED,
        VELOCITY_EXCEEDED,
        BEHAVIOR_ANOMALY,
        DEVICE_MISMATCH,
        LOCATION_ANOMALY,
        NETWORK_RISK
    }
    
    public enum FraudLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    /**
     * Get entity ID for this fraud event - overridden to handle String transactionId
     */
    @Override
    public UUID getEntityId() {
        UUID parentEntityId = super.getEntityId();
        if (parentEntityId != null) {
            return parentEntityId;
        }
        // Try to parse transactionId as UUID if it's set
        if (transactionId != null) {
            try {
                return UUID.fromString(transactionId);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Get risk level mapped from fraud level
     */
    public RiskLevel getRiskLevel() {
        if (fraudLevel == null) {
            return RiskLevel.LOW;
        }
        
        return switch (fraudLevel) {
            case LOW -> RiskLevel.LOW;
            case MEDIUM -> RiskLevel.MEDIUM;
            case HIGH -> RiskLevel.HIGH;
            case CRITICAL -> RiskLevel.CRITICAL;
        };
    }
}