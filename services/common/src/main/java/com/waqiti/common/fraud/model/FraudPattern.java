package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive fraud pattern detection model with behavioral analysis
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class FraudPattern {
    
    private String patternId;
    private String patternName;
    private String description;
    private PatternType patternType;
    private PatternCategory category;
    
    // Pattern characteristics
    private Double confidence;
    private Double severity;
    private Double riskScore; // Numeric risk assessment
    private FraudRiskLevel riskLevel;
    private String primaryIndicator;
    private List<String> secondaryIndicators;
    
    // Detection details
    private LocalDateTime detectedAt;
    private String detectionMethod;
    private String detectionModel;
    private String modelVersion;
    private Long detectionLatencyMs;
    private String matchedValue; // The actual value that matched the fraud pattern (masked for security)
    
    // Transaction context
    private String transactionId;
    private String userId;
    private String accountId;
    private String sessionId;
    private String deviceId;
    private String ipAddress;
    
    // Pattern metrics
    private Integer occurrenceCount;
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal maxAmount;
    
    // Temporal patterns
    private List<LocalDateTime> occurrenceTimes;
    private Double frequencyScore;
    private String timePattern;
    private Boolean isUnusualTiming;
    private Integer hoursOfDayPattern;
    private Integer dayOfWeekPattern;
    
    // Behavioral patterns
    private Map<String, Double> behavioralMetrics;
    private Double velocityScore;
    private Double amountVarianceScore;
    private Double locationVarianceScore;
    private Double deviceVarianceScore;
    
    // Related entities
    private List<String> relatedTransactions;
    private List<String> relatedUsers;
    private List<String> relatedDevices;
    private List<String> relatedIpAddresses;
    
    // Pattern validation
    private ValidationStatus validationStatus;
    private Boolean isFalsePositive;
    private String falsePositiveReason;
    private String validatedBy;
    private LocalDateTime validatedAt;
    
    // Additional metadata
    private Map<String, Object> patternData;
    private List<String> tags;
    private String notes;
    
    /**
     * Types of fraud patterns
     */
    public enum PatternType {
        // Generic pattern types (used by detection systems)
        STATIC,                  // Static rule-based patterns
        BEHAVIORAL,              // Behavioral analysis patterns
        NETWORK,                 // Network-based patterns
        TRANSACTION,             // Transaction-based patterns
        ML_DETECTED,             // Machine learning detected patterns
        VELOCITY,                // Velocity-based patterns
        GEOLOCATION,             // Location-based patterns
        DEVICE,                  // Device-based patterns
        ACCOUNT,                 // Account-based patterns
        EMAIL,                   // Email-based patterns
        IP,                      // IP-based patterns

        // Specific fraud pattern types
        VELOCITY_ABUSE,          // High transaction velocity
        AMOUNT_STRUCTURING,      // Structured amounts to avoid detection
        ACCOUNT_TAKEOVER,        // Account compromise patterns
        SYNTHETIC_IDENTITY,      // Fake identity patterns
        MONEY_LAUNDERING,        // Money laundering patterns
        CARD_TESTING,            // Credit card testing patterns
        MERCHANT_COLLUSION,      // Merchant fraud patterns
        REFUND_ABUSE,            // Refund fraud patterns
        BONUS_ABUSE,             // Promotional abuse patterns
        ARBITRAGE,               // Price arbitrage patterns
        WASH_TRADING,            // Wash trading patterns
        PUMP_DUMP,               // Market manipulation patterns
        SOCIAL_ENGINEERING,      // Social engineering patterns
        BEHAVIORAL_ANOMALY,      // Unusual behavioral patterns
        NETWORK_ANALYSIS,        // Network-based fraud patterns
        DEVICE_SPOOFING,         // Device fingerprint spoofing
        LOCATION_SPOOFING,       // GPS/location spoofing
        TIME_BASED_ATTACK,       // Time-based fraud patterns
        MULTI_ACCOUNT_ABUSE,     // Multiple account abuse
        AUTOMATED_ATTACK         // Bot/automation patterns
    }
    
    /**
     * Categories for organizing patterns
     */
    public enum PatternCategory {
        TRANSACTION_BASED,
        BEHAVIORAL,
        NETWORK,
        TEMPORAL,
        GEOGRAPHICAL,
        DEVICE_BASED,
        ACCOUNT_BASED,
        PAYMENT_METHOD,
        MERCHANT_RELATED,
        COMPLIANCE_RELATED
    }
    
    /**
     * Pattern validation status
     */
    public enum ValidationStatus {
        PENDING,
        CONFIRMED,
        FALSE_POSITIVE,
        INCONCLUSIVE,
        EXPIRED
    }
    
    /**
     * Get risk score (returns riskScore field or calculates it)
     */
    public Double getRiskScore() {
        return riskScore != null ? riskScore : calculateRiskScore();
    }

    /**
     * Calculate overall pattern risk score
     */
    public double calculateRiskScore() {
        double baseScore = 0.0;
        
        // Base score from confidence and severity
        if (confidence != null) {
            baseScore += confidence * 0.4;
        }
        
        if (severity != null) {
            baseScore += severity * 0.3;
        }
        
        // Add frequency component
        if (frequencyScore != null) {
            baseScore += frequencyScore * 0.2;
        }
        
        // Add behavioral metrics
        if (behavioralMetrics != null) {
            double behavioralAverage = behavioralMetrics.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            baseScore += behavioralAverage * 0.1;
        }
        
        // Adjust for pattern type severity
        baseScore *= getPatternTypeSeverityMultiplier();
        
        // Adjust for validation status
        if (validationStatus == ValidationStatus.CONFIRMED) {
            baseScore *= 1.2;
        } else if (validationStatus == ValidationStatus.FALSE_POSITIVE) {
            baseScore *= 0.1;
        }
        
        return Math.min(1.0, baseScore);
    }
    
    /**
     * Get severity multiplier based on pattern type
     */
    private double getPatternTypeSeverityMultiplier() {
        switch (patternType) {
            case MONEY_LAUNDERING:
            case ACCOUNT_TAKEOVER:
            case SYNTHETIC_IDENTITY:
                return 1.5;
            
            case VELOCITY_ABUSE:
            case AMOUNT_STRUCTURING:
            case MERCHANT_COLLUSION:
                return 1.3;
            
            case REFUND_ABUSE:
            case BONUS_ABUSE:
            case CARD_TESTING:
                return 1.1;
            
            case BEHAVIORAL_ANOMALY:
            case DEVICE_SPOOFING:
            case LOCATION_SPOOFING:
                return 1.0;
            
            default:
                return 0.8;
        }
    }
    
    /**
     * Check if pattern requires immediate investigation
     */
    public boolean requiresImmediateInvestigation() {
        double riskScore = calculateRiskScore();
        
        return riskScore > 0.8 ||
               patternType == PatternType.MONEY_LAUNDERING ||
               patternType == PatternType.ACCOUNT_TAKEOVER ||
               (confidence != null && confidence > 0.9 && severity != null && severity > 0.8);
    }
    
    /**
     * Generate pattern significance score based on multiple factors
     */
    public double calculateSignificanceScore() {
        double score = 0.0;
        
        // Frequency significance
        if (occurrenceCount != null && occurrenceCount > 1) {
            score += Math.min(0.3, occurrenceCount * 0.05);
        }
        
        // Amount significance
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.valueOf(1000)) > 0) {
            score += Math.min(0.3, totalAmount.doubleValue() / 100000.0);
        }
        
        // Time span significance
        if (firstOccurrence != null && lastOccurrence != null) {
            long daysBetween = java.time.Duration.between(firstOccurrence, lastOccurrence).toDays();
            if (daysBetween < 1) {
                score += 0.2; // High frequency in short time
            } else if (daysBetween < 7) {
                score += 0.1; // Pattern over a week
            }
        }
        
        // Network effect significance
        if (relatedUsers != null && relatedUsers.size() > 1) {
            score += Math.min(0.2, relatedUsers.size() * 0.02);
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get recommended actions based on pattern characteristics
     */
    public List<String> getRecommendedActions() {
        double riskScore = calculateRiskScore();
        
        if (requiresImmediateInvestigation()) {
            return List.of(
                "ESCALATE_TO_ANALYST",
                "FREEZE_ACCOUNT",
                "BLOCK_TRANSACTION",
                "NOTIFY_COMPLIANCE",
                "GENERATE_SAR"
            );
        }
        
        if (riskScore > 0.6) {
            return List.of(
                "REQUIRE_ADDITIONAL_AUTH",
                "ENABLE_ENHANCED_MONITORING",
                "LIMIT_TRANSACTION_AMOUNT",
                "LOG_SECURITY_EVENT"
            );
        }
        
        if (riskScore > 0.4) {
            return List.of(
                "ENABLE_ENHANCED_MONITORING",
                "LOG_SECURITY_EVENT",
                "SEND_SECURITY_ALERT"
            );
        }
        
        return List.of("LOG_SECURITY_EVENT");
    }
    
    /**
     * Generate human-readable pattern description
     */
    public String generatePatternDescription() {
        StringBuilder desc = new StringBuilder();
        
        desc.append("Fraud Pattern: ").append(patternName != null ? patternName : patternType);
        
        if (occurrenceCount != null && occurrenceCount > 1) {
            desc.append(" (").append(occurrenceCount).append(" occurrences)");
        }
        
        if (confidence != null) {
            desc.append(" with ").append(String.format("%.1f%%", confidence * 100)).append(" confidence");
        }
        
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            desc.append(", total amount: $").append(totalAmount);
        }
        
        if (primaryIndicator != null) {
            desc.append(" - Primary indicator: ").append(primaryIndicator);
        }
        
        return desc.toString();
    }
    
    /**
     * Check if pattern is still active/relevant
     */
    public boolean isActivePattern() {
        if (validationStatus == ValidationStatus.FALSE_POSITIVE || 
            validationStatus == ValidationStatus.EXPIRED) {
            return false;
        }
        
        if (lastOccurrence != null) {
            // Consider pattern inactive if no occurrences in last 30 days
            return lastOccurrence.isAfter(LocalDateTime.now().minusDays(30));
        }
        
        return true;
    }
}