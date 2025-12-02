/**
 * AML Check Request DTO
 * Used for requesting Anti-Money Laundering checks
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
public class AMLCheckRequest {
    
    /**
     * Request action (SCREEN, RE_SCREEN, UPDATE, CLEAR)
     */
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "SCREEN|RE_SCREEN|UPDATE|CLEAR", 
             message = "Action must be SCREEN, RE_SCREEN, UPDATE, or CLEAR")
    private String action;
    
    /**
     * User ID to perform AML check on
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Check type (CUSTOMER_SCREENING, TRANSACTION_MONITORING, PERIODIC_REVIEW, ALERT_TRIGGERED)
     */
    @NotBlank(message = "Check type is required")
    @Pattern(regexp = "CUSTOMER_SCREENING|TRANSACTION_MONITORING|PERIODIC_REVIEW|ALERT_TRIGGERED", 
             message = "Check type must be CUSTOMER_SCREENING, TRANSACTION_MONITORING, PERIODIC_REVIEW, or ALERT_TRIGGERED")
    private String checkType;
    
    /**
     * Check scope (BASIC, STANDARD, COMPREHENSIVE, ENHANCED)
     */
    @Pattern(regexp = "BASIC|STANDARD|COMPREHENSIVE|ENHANCED", 
             message = "Check scope must be BASIC, STANDARD, COMPREHENSIVE, or ENHANCED")
    private String checkScope;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, URGENT)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "Priority must be LOW, MEDIUM, HIGH, or URGENT")
    private String priority;
    
    /**
     * Customer information for screening
     */
    @NotNull(message = "Customer information is required")
    private Map<String, Object> customerInfo;
    
    /**
     * Transaction ID if check is transaction-related
     */
    private String transactionId;
    
    /**
     * Transaction details for monitoring
     */
    private Map<String, Object> transactionDetails;
    
    /**
     * Transaction amount if applicable
     */
    @DecimalMin(value = "0.0", message = "Amount must be non-negative")
    private BigDecimal transactionAmount;
    
    /**
     * Transaction currency
     */
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be 3-letter ISO code")
    private String transactionCurrency;
    
    /**
     * Counterparty information
     */
    private Map<String, Object> counterpartyInfo;
    
    /**
     * Geographic information
     */
    private Map<String, Object> geographicInfo;
    
    /**
     * AML screening lists to check against
     */
    private List<String> screeningLists;
    
    /**
     * Sanctions lists to check
     */
    private List<String> sanctionsLists;
    
    /**
     * PEP lists to check
     */
    private List<String> pepLists;
    
    /**
     * Watchlists to check
     */
    private List<String> watchlists;
    
    /**
     * Third-party screening providers to use
     */
    private List<String> screeningProviders;
    
    /**
     * Fuzzy matching threshold (0.0 to 1.0)
     */
    @DecimalMin(value = "0.0", message = "Threshold must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Threshold must be between 0.0 and 1.0")
    private Double fuzzyMatchThreshold;
    
    /**
     * Whether to include historical data
     */
    private Boolean includeHistoricalData;
    
    /**
     * Historical data period in days
     */
    @Min(value = 1, message = "Historical period must be at least 1 day")
    private Integer historicalPeriodDays;
    
    /**
     * Whether to perform behavioral analysis
     */
    private Boolean performBehavioralAnalysis;
    
    /**
     * Whether to check beneficial owners (for business accounts)
     */
    private Boolean checkBeneficialOwners;
    
    /**
     * Beneficial owners list
     */
    private List<Map<String, Object>> beneficialOwners;
    
    /**
     * Whether to perform network analysis
     */
    private Boolean performNetworkAnalysis;
    
    /**
     * Network analysis depth
     */
    @Min(value = 1, message = "Network depth must be at least 1")
    @Max(value = 5, message = "Network depth cannot exceed 5")
    private Integer networkAnalysisDepth;
    
    /**
     * Risk indicators to check for
     */
    private List<String> riskIndicators;
    
    /**
     * Typologies to screen for
     */
    private List<String> typologies;
    
    /**
     * Alert ID that triggered this check
     */
    private String triggeringAlertId;
    
    /**
     * Previous AML check ID (for re-screening)
     */
    private String previousCheckId;
    
    /**
     * Reason for the AML check
     */
    @NotBlank(message = "Check reason is required")
    private String checkReason;
    
    /**
     * Special instructions
     */
    private String specialInstructions;
    
    /**
     * Compliance officer requesting the check
     */
    private String requestedBy;
    
    /**
     * Request source (SYSTEM, MANUAL, SCHEDULED, ALERT)
     */
    @Pattern(regexp = "SYSTEM|MANUAL|SCHEDULED|ALERT", 
             message = "Request source must be SYSTEM, MANUAL, SCHEDULED, or ALERT")
    private String requestSource;
    
    /**
     * Expected completion time
     */
    private Instant expectedCompletionTime;
    
    /**
     * Deadline for completion
     */
    private Instant completionDeadline;
    
    /**
     * Additional screening parameters
     */
    private Map<String, Object> screeningParameters;
    
    /**
     * Additional metadata
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