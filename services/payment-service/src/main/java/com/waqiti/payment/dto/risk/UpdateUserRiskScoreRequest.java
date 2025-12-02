/**
 * Update User Risk Score Request DTO
 * Used for updating user risk scores based on fraud containment execution
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRiskScoreRequest {
    
    /**
     * User ID for whom risk score is being updated
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Alert ID that triggered the risk score update
     */
    @NotBlank(message = "Alert ID is required")
    private String alertId;
    
    /**
     * Type of fraud detected
     */
    @NotBlank(message = "Fraud type is required")
    private String fraudType;
    
    /**
     * Fraud score (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "Fraud score must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Fraud score must be between 0.0 and 1.0")
    private Double fraudScore;
    
    /**
     * Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Risk level must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String riskLevel;
    
    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Severity must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String severity;
    
    /**
     * List of containment actions taken
     */
    private List<String> containmentActions;
    
    /**
     * Whether account was suspended
     */
    private Boolean accountSuspended;
    
    /**
     * Whether cards were blocked
     */
    private Boolean cardsBlocked;
    
    /**
     * Whether enhanced monitoring was enabled
     */
    private Boolean enhancedMonitoringEnabled;
    
    /**
     * Transaction ID if applicable
     */
    private String transactionId;
    
    /**
     * Transaction amount if applicable
     */
    @DecimalMin(value = "0.0", message = "Amount must be non-negative")
    private BigDecimal amount;
    
    /**
     * Currency code
     */
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    /**
     * Fraud indicators detected
     */
    private Map<String, Object> fraudIndicators;
    
    /**
     * When fraud was detected
     */
    private Instant detectedAt;
    
    /**
     * When containment was executed
     */
    private Instant executedAt;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
}