/**
 * Create Compliance Alert Request DTO
 * Used for creating compliance alerts following fraud containment execution
 */
package com.waqiti.payment.dto.compliance;

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
public class CreateComplianceAlertRequest {
    
    /**
     * Type of compliance alert
     */
    @NotBlank(message = "Alert type is required")
    private String alertType;
    
    /**
     * Original alert ID that triggered this compliance alert
     */
    @NotBlank(message = "Alert ID is required")
    private String alertId;
    
    /**
     * User ID associated with the alert
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Transaction ID if applicable
     */
    private String transactionId;
    
    /**
     * Type of fraud detected
     */
    @NotBlank(message = "Fraud type is required")
    private String fraudType;
    
    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Severity must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String severity;
    
    /**
     * Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Risk level must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String riskLevel;
    
    /**
     * Fraud score (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "Fraud score must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Fraud score must be between 0.0 and 1.0")
    private Double fraudScore;
    
    /**
     * Containment actions taken
     */
    private List<String> containmentActions;
    
    /**
     * Reason for containment
     */
    private String containmentReason;
    
    /**
     * Affected accounts
     */
    private List<String> affectedAccounts;
    
    /**
     * Affected cards
     */
    private List<String> affectedCards;
    
    /**
     * Affected transactions
     */
    private List<String> affectedTransactions;
    
    /**
     * Transaction amount if applicable
     */
    @DecimalMin(value = "0.0", message = "Amount must be non-negative")
    private BigDecimal transactionAmount;
    
    /**
     * Transaction currency
     */
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String currency;
    
    /**
     * Current account status
     */
    private String accountStatus;
    
    /**
     * Whether regulator notification is required
     */
    private Boolean requiresRegulatorNotification;
    
    /**
     * Whether investigation is required
     */
    private Boolean requiresInvestigation;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority;
    
    /**
     * When fraud was detected
     */
    private Instant detectedAt;
    
    /**
     * When containment was executed
     */
    private Instant executedAt;
    
    /**
     * Who executed the containment
     */
    private String executedBy;
    
    /**
     * Source of execution (SYSTEM, MANUAL, AUTOMATED)
     */
    @Pattern(regexp = "SYSTEM|MANUAL|AUTOMATED", message = "Execution source must be SYSTEM, MANUAL, or AUTOMATED")
    private String executionSource;
    
    /**
     * Additional metadata for compliance tracking
     */
    private Map<String, Object> metadata;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Request timestamp
     */
    private Instant requestTime;
}