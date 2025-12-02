package com.waqiti.common.fraud;

import com.waqiti.common.fraud.alert.FraudAlert;
import com.waqiti.common.fraud.model.FraudRuleViolation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enhanced fraud context for comprehensive fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedFraudContext {
    
    private String contextId;
    private String transactionId;
    private String userId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime timestamp;
    
    // Transaction and device data
    private Map<String, Object> transactionData;
    private Map<String, Object> deviceInfo;
    
    // Location information
    private Map<String, Object> locationInfo;
    
    // Historical and external data
    private Map<String, Object> historicalData;
    private Map<String, Object> externalData;
    
    // Risk assessment
    private double overallRiskScore;
    
    // Rule violations and alerts
    private List<FraudRuleViolation> ruleViolations;
    private List<FraudAlert> fraudAlerts;
    
    // Metadata and tracking
    private Map<String, Object> metadata;
    private LocalDateTime lastEnriched;
    private LocalDateTime archivedAt;
    
    /**
     * Check if context is high risk
     */
    public boolean isHighRisk() {
        return overallRiskScore >= 0.7;
    }
    
    /**
     * Check if context has violations
     */
    public boolean hasViolations() {
        return ruleViolations != null && !ruleViolations.isEmpty();
    }
    
    /**
     * Check if context has alerts
     */
    public boolean hasAlerts() {
        return fraudAlerts != null && !fraudAlerts.isEmpty();
    }
}