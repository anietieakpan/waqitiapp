package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Individual fraud indicator detected during analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudIndicator {
    
    private String indicatorType;
    private String description;
    private double weight;
    private double confidence;
    private String source;
    
    /**
     * Constructor for type, description, and weight
     */
    public FraudIndicator(String type, String description, double weight) {
        this.indicatorType = type;
        this.description = description;
        this.weight = weight;
        this.confidence = 0.8; // Default confidence
    }
    
    /**
     * Get indicator type (alias for indicatorType for compatibility)
     */
    public String getType() {
        return indicatorType;
    }
    
    /**
     * Common indicator types
     */
    public static class Type {
        public static final String HIGH_VELOCITY = "HIGH_VELOCITY";
        public static final String UNUSUAL_LOCATION = "UNUSUAL_LOCATION";
        public static final String DEVICE_CHANGE = "DEVICE_CHANGE";
        public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";
        public static final String BLACKLISTED = "BLACKLISTED";
        public static final String ML_ANOMALY = "ML_ANOMALY";
        public static final String RULE_VIOLATION = "RULE_VIOLATION";
    }
    
    /**
     * Create a high-risk indicator
     */
    public static FraudIndicator highRisk(String type, String description) {
        return FraudIndicator.builder()
            .indicatorType(type)
            .description(description)
            .weight(0.8)
            .confidence(0.9)
            .build();
    }
    
    /**
     * Create a medium-risk indicator
     */
    public static FraudIndicator mediumRisk(String type, String description) {
        return FraudIndicator.builder()
            .indicatorType(type)
            .description(description)
            .weight(0.5)
            .confidence(0.7)
            .build();
    }
}