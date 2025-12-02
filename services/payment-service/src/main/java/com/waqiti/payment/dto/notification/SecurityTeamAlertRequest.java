/**
 * Security Team Alert Request DTO
 * Used for sending fraud containment alerts to security team
 */
package com.waqiti.payment.dto.notification;

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
public class SecurityTeamAlertRequest {
    
    /**
     * Alert type for security team
     */
    @NotBlank(message = "Alert type is required")
    private String alertType;
    
    /**
     * Alert ID that triggered this notification
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
     * Fraud score (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "Fraud score must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Fraud score must be between 0.0 and 1.0")
    private Double fraudScore;
    
    /**
     * Containment actions taken
     */
    @NotEmpty(message = "Containment actions are required")
    private List<String> containmentActions;
    
    /**
     * Number of containment actions executed
     */
    @Min(value = 0, message = "Containment actions count cannot be negative")
    private Integer containmentActionsCount;
    
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
     * Affected accounts list
     */
    private List<String> affectedAccounts;
    
    /**
     * Affected cards list
     */
    private List<String> affectedCards;
    
    /**
     * Affected transactions list
     */
    private List<String> affectedTransactions;
    
    /**
     * Response time in milliseconds for containment
     */
    @Min(value = 0, message = "Response time cannot be negative")
    private Long responseTimeMs;
    
    /**
     * When fraud was detected
     */
    @NotNull(message = "Detection time is required")
    private Instant detectedAt;
    
    /**
     * When containment was executed
     */
    @NotNull(message = "Execution time is required")
    private Instant executedAt;
    
    /**
     * Whether investigation is required
     */
    private Boolean requiresInvestigation;
    
    /**
     * Priority level for security team (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority;
    
    /**
     * Alert title for security team
     */
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;
    
    /**
     * Alert description for security team
     */
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    /**
     * Alert summary for quick reference
     */
    @Size(max = 500, message = "Summary cannot exceed 500 characters")
    private String summary;
    
    /**
     * Recommended immediate actions
     */
    private List<String> recommendedActions;
    
    /**
     * Escalation level (LEVEL_1, LEVEL_2, LEVEL_3, MANAGEMENT)
     */
    @Pattern(regexp = "LEVEL_1|LEVEL_2|LEVEL_3|MANAGEMENT", 
             message = "Escalation level must be LEVEL_1, LEVEL_2, LEVEL_3, or MANAGEMENT")
    private String escalationLevel;
    
    /**
     * Team to assign alert to (FRAUD_TEAM, SECURITY_TEAM, SOC, COMPLIANCE)
     */
    @Pattern(regexp = "FRAUD_TEAM|SECURITY_TEAM|SOC|COMPLIANCE", 
             message = "Assigned team must be FRAUD_TEAM, SECURITY_TEAM, SOC, or COMPLIANCE")
    private String assignedTeam;
    
    /**
     * Specific analyst to assign to
     */
    private String assignedAnalyst;
    
    /**
     * SLA deadline for response
     */
    private Instant slaDeadline;
    
    /**
     * Alert category for classification
     */
    private String alertCategory;
    
    /**
     * Alert tags for filtering and search
     */
    private List<String> tags;
    
    /**
     * Risk indicators detected
     */
    private List<String> riskIndicators;
    
    /**
     * Fraud indicators found
     */
    private List<String> fraudIndicators;
    
    /**
     * Related alerts or incidents
     */
    private List<String> relatedAlerts;
    
    /**
     * Customer risk profile summary
     */
    private Map<String, Object> customerRiskProfile;
    
    /**
     * Transaction pattern analysis
     */
    private Map<String, Object> transactionPatterns;
    
    /**
     * Device and location information
     */
    private Map<String, Object> deviceLocationInfo;
    
    /**
     * System impact assessment
     */
    private Map<String, Object> systemImpact;
    
    /**
     * Business impact assessment
     */
    private Map<String, Object> businessImpact;
    
    /**
     * Evidence and artifacts
     */
    private List<String> evidence;
    
    /**
     * Additional context for investigation
     */
    private Map<String, Object> investigationContext;
    
    /**
     * Regulatory implications
     */
    private List<String> regulatoryImplications;
    
    /**
     * Notification channels for security team
     */
    private List<String> notificationChannels;
    
    /**
     * Whether this requires immediate escalation
     */
    private Boolean immediateEscalation;
    
    /**
     * Whether 24/7 on-call should be notified
     */
    private Boolean notifyOnCall;
    
    /**
     * Additional security metadata
     */
    private Map<String, Object> securityMetadata;
    
    /**
     * Source system generating the alert
     */
    private String sourceSystem;
    
    /**
     * Alert version for tracking
     */
    private String alertVersion;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Request timestamp
     */
    private Instant timestamp;
}