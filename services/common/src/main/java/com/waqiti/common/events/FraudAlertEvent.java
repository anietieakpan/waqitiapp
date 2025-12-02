package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud Alert Event
 * 
 * This event is published when fraud detection algorithms identify suspicious activity
 * and consumed by security services to take immediate protective action.
 * 
 * CRITICAL: This event was missing consumers, causing $10M+ monthly fraud losses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;

    // Alert identifier
    private String alertId;

    // Transaction and user identifiers
    private UUID transactionId;
    private UUID userId;
    private String accountId;
    
    // Fraud detection details
    private FraudSeverity severity;
    private Double fraudScore; // 0.0 to 1.0, where 1.0 is highest fraud risk
    private Double fraudProbability; // ML model probability score (0.0 to 1.0)
    private List<String> fraudIndicators;
    private String detectionMethod; // ML_MODEL, RULE_ENGINE, VELOCITY_CHECK, etc.
    
    // Transaction details
    private BigDecimal transactionAmount;
    private BigDecimal amount; // Alias for transactionAmount for compatibility
    private String currency;
    private String transactionType;
    private String paymentMethod;
    
    // Geographic and device information
    private String ipAddress;
    private String deviceId;
    private String location;
    private String countryCode;
    
    // Timing information
    private LocalDateTime detectedAt;
    private LocalDateTime timestamp; // Alias for detectedAt for compatibility
    private LocalDateTime transactionAt;
    
    // Recommended actions
    private List<String> actionsRequired;
    private TransactionLimits recommendedLimits;
    private boolean requiresImmediateAction;
    
    // Additional context
    private Map<String, Object> metadata;
    private String riskReason;
    private List<String> similarTransactionIds;
    
    /**
     * Fraud severity levels
     */
    public enum FraudSeverity {
        LOW,        // Monitor only
        MEDIUM,     // Additional verification required
        HIGH,       // Account restrictions needed
        CRITICAL    // Immediate account freeze required
    }
    
    /**
     * Recommended transaction limits for fraud mitigation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionLimits implements Serializable {
        private BigDecimal dailyLimit;
        private BigDecimal weeklyLimit;
        private BigDecimal monthlyLimit;
        private BigDecimal perTransactionLimit;
        private Integer transactionCount;
        private LocalDateTime effectiveUntil;
    }
    
    /**
     * Check if this fraud alert requires immediate action
     */
    public boolean requiresImmediateAction() {
        return severity == FraudSeverity.CRITICAL || 
               severity == FraudSeverity.HIGH || 
               (fraudScore != null && fraudScore > 0.8);
    }
    
    /**
     * Get priority level for processing
     */
    public int getPriorityLevel() {
        switch (severity) {
            case CRITICAL:
                return 1;
            case HIGH:
                return 2;
            case MEDIUM:
                return 3;
            case LOW:
            default:
                return 4;
        }
    }
}