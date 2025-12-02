package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Defines a known fraud pattern used for pattern matching.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudPattern {

    private String patternId;
    private String patternName;
    private String description;

    // Pattern classification
    private PatternType type;
    private PatternSeverity severity;

    // Pattern characteristics
    private List<String> indicators; // Indicators that define this pattern
    private Map<String, Object> rules; // Rules for pattern matching

    // Pattern metadata
    private Integer matchCount; // How many times this pattern has been detected
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private Double detectionAccuracy; // Historical accuracy of this pattern

    // Risk assessment
    private Double riskWeight; // Weight applied when this pattern is detected
    private Boolean autoBlock; // Should auto-block if this pattern detected

    /**
     * Pattern types
     */
    public enum PatternType {
        STRUCTURING, // Breaking up large amounts to avoid detection
        RAPID_FIRE, // Multiple quick transactions
        ACCOUNT_TAKEOVER, // ATO indicators
        MONEY_MULE, // Mule account patterns
        CARD_TESTING, // Testing stolen cards
        VELOCITY_ABUSE, // Unusual transaction velocity
        ROUND_AMOUNT, // Suspiciously round amounts
        SMURFING, // Distributing funds across many accounts
        LAYERING, // Complex transaction chains to hide origin
        CUCKOO_SMURFING, // Third-party transfers to conceal funds
        INTEGRATION, // Bringing illicit funds into legitimate economy
        REFUND_FRAUD, // Fraudulent refund requests
        PROMO_ABUSE, // Promotional offer abuse
        SYNTHETIC_IDENTITY, // Synthetic identity fraud
        BUSINESS_EMAIL_COMPROMISE // BEC patterns
    }

    /**
     * Pattern severity
     */
    public enum PatternSeverity {
        LOW(0.2),
        MEDIUM(0.5),
        HIGH(0.7),
        CRITICAL(0.9);

        private final double riskMultiplier;

        PatternSeverity(double riskMultiplier) {
            this.riskMultiplier = riskMultiplier;
        }

        public double getRiskMultiplier() {
            return riskMultiplier;
        }
    }
}
