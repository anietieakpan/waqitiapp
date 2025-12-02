package com.waqiti.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enterprise-grade fraud alert event with complete validation and metadata.
 * Implements comprehensive fraud detection data model for production use.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FraudAlertEvent {
    
    // ======================== Event Metadata ========================
    @NotBlank(message = "Event ID is required")
    @Pattern(regexp = "^[A-Z0-9]{8}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{12}$")
    private String eventId;
    
    @NotNull(message = "Timestamp is required")
    @PastOrPresent(message = "Timestamp cannot be in the future")
    private LocalDateTime timestamp;
    
    @NotBlank(message = "Source service is required")
    @Size(max = 100)
    private String source;
    
    @JsonProperty("correlation_id")
    private String correlationId;
    
    @NotNull
    @Min(1)
    private Integer version = 1;
    
    // ======================== Fraud Core Details ========================
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Account ID is required")
    private String accountId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 15, fraction = 2)
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;
    
    @NotNull(message = "Severity is required")
    private FraudSeverity severity;
    
    @NotNull(message = "Risk score is required")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double riskScore;
    
    @NotBlank(message = "Reason is required")
    @Size(max = 1000)
    private String reason;
    
    @NotNull(message = "Fraud type is required")
    private FraudType fraudType;
    
    @Builder.Default
    private FraudSubType fraudSubType = FraudSubType.UNSPECIFIED;
    
    // ======================== Transaction Context ========================
    private String merchantId;
    private String merchantName;
    private String merchantCategoryCode;
    
    @NotBlank
    private String transactionType;
    
    @NotBlank
    private String paymentMethod;
    
    @NotNull
    @Builder.Default
    private Boolean reversible = false;
    
    private String originalTransactionId;
    private LocalDateTime transactionInitiatedAt;
    private String transactionChannel;
    
    @Builder.Default
    private TransactionStatus transactionStatus = TransactionStatus.PENDING;
    
    // ======================== User Behavioral Context ========================
    @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$|^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$")
    private String userIpAddress;
    
    private String deviceId;
    private String deviceFingerprint;
    private String sessionId;
    
    @NotNull
    @Builder.Default
    private Boolean userOnline = false;
    
    private String geolocation;
    private Double latitude;
    private Double longitude;
    private String country;
    private String city;
    private String timezone;
    
    // User behavior metrics
    private Integer recentFailedAttempts;
    private LocalDateTime lastSuccessfulTransaction;
    private Integer dailyTransactionCount;
    private BigDecimal dailyTransactionVolume;
    private Boolean unusualTimeOfActivity;
    private Double velocityScore;
    
    // ======================== Device & Network Intelligence ========================
    private String userAgent;
    private String browserLanguage;
    private String operatingSystem;
    private Boolean vpnDetected;
    private Boolean torDetected;
    private Boolean proxyDetected;
    private String networkCarrier;
    private String connectionType;
    private Integer deviceTrustScore;
    private Boolean jailbrokenDevice;
    private Boolean emulatorDetected;
    private Set<String> installedSecurityApps;
    
    // ======================== ML Model & Detection Details ========================
    @NotNull
    private Map<String, Object> detectionRules;
    
    @NotNull
    private Map<String, Double> riskFactors;
    
    private String mlModelVersion;
    private String mlModelName;
    
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double confidenceScore;
    
    private Map<String, Double> featureImportance;
    private List<String> triggeredRules;
    private Map<String, Object> modelExplanation;
    
    // ======================== Historical Context ========================
    private Integer userAccountAgeDays;
    private Integer previousFraudCount;
    private LocalDateTime lastFraudAttempt;
    private BigDecimal totalFraudAmount;
    private List<String> previousFraudTypes;
    private Double historicalRiskScore;
    private Boolean repeatOffender;
    
    // ======================== Business Context ========================
    @Builder.Default
    private Boolean requiresImmediateAction = false;
    
    @Builder.Default
    private Boolean notifyUser = true;
    
    @Builder.Default
    private Boolean notifyMerchant = false;
    
    private String suggestedAction;
    private List<String> recommendedActions;
    private Integer priorityLevel;
    private String assignedAnalyst;
    private String caseId;
    
    // ======================== Compliance & Regulatory ========================
    private Boolean amlFlagged;
    private Boolean sanctionsHit;
    private Boolean pepMatch;
    private String regulatoryJurisdiction;
    private List<String> complianceFlags;
    private Boolean sarRequired;
    private String reportingRequirement;
    
    // ======================== Financial Impact ========================
    private BigDecimal potentialLoss;
    private BigDecimal actualLoss;
    private BigDecimal recoveredAmount;
    private String chargebackStatus;
    private LocalDateTime chargebackDeadline;
    private BigDecimal liabilityAmount;
    private String liabilityHolder;
    
    // ======================== Investigation Support ========================
    private Map<String, String> evidenceLinks;
    private List<String> relatedTransactionIds;
    private List<String> relatedUserIds;
    private Map<String, Object> forensicData;
    private String investigationNotes;
    private InvestigationStatus investigationStatus;
    
    // ======================== Response Tracking ========================
    private LocalDateTime detectedAt;
    private LocalDateTime respondedAt;
    private Long responseTimeMs;
    private String responseAction;
    private String responseOutcome;
    private Boolean falsePositive;
    private String falsePositiveReason;
    
    // ======================== Enums ========================
    public enum FraudSeverity {
        CRITICAL(1),    // Immediate automated block required
        HIGH(2),        // Urgent manual review required
        MEDIUM(3),      // Enhanced monitoring needed
        LOW(4),         // Log and monitor
        INFO(5);        // Informational only
        
        private final int priority;
        
        FraudSeverity(int priority) {
            this.priority = priority;
        }
        
        public int getPriority() {
            return priority;
        }
    }
    
    public enum FraudType {
        ACCOUNT_TAKEOVER,
        CARD_NOT_PRESENT,
        CARD_PRESENT,
        IDENTITY_THEFT,
        SYNTHETIC_IDENTITY,
        MONEY_LAUNDERING,
        TERRORIST_FINANCING,
        VELOCITY_ABUSE,
        DEVICE_FINGERPRINT_MISMATCH,
        LOCATION_ANOMALY,
        BEHAVIORAL_ANOMALY,
        BLACKLISTED_ENTITY,
        CHARGEBACK_FRAUD,
        FRIENDLY_FRAUD,
        MERCHANT_FRAUD,
        COLLUSION_FRAUD,
        FIRST_PARTY_FRAUD,
        THIRD_PARTY_FRAUD,
        APPLICATION_FRAUD,
        BUST_OUT_FRAUD,
        SOCIAL_ENGINEERING,
        PHISHING_VICTIM,
        MALWARE_INFECTION,
        MAN_IN_THE_MIDDLE,
        SIM_SWAP,
        OTHER
    }
    
    public enum FraudSubType {
        UNSPECIFIED,
        STOLEN_CARD,
        LOST_CARD,
        COUNTERFEIT_CARD,
        CARD_TESTING,
        ACCOUNT_ENUMERATION,
        CREDENTIAL_STUFFING,
        BRUTE_FORCE,
        SESSION_HIJACKING,
        COOKIE_THEFT,
        API_ABUSE,
        BOT_ATTACK,
        MULTIPLE_ACCOUNTS,
        MULE_ACCOUNT,
        SLEEPER_FRAUD,
        TRIANGULATION_FRAUD,
        REFUND_FRAUD,
        PROMOTION_ABUSE,
        RESELLER_FRAUD
    }
    
    public enum TransactionStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED,
        CANCELLED,
        BLOCKED,
        UNDER_REVIEW,
        ESCALATED
    }
    
    public enum InvestigationStatus {
        NOT_STARTED,
        IN_PROGRESS,
        AWAITING_INFO,
        ESCALATED,
        RESOLVED_FRAUD,
        RESOLVED_LEGITIMATE,
        CLOSED,
        REOPENED
    }
    
    // ======================== Validation Methods ========================
    public boolean isHighRisk() {
        return riskScore >= 0.7 || severity == FraudSeverity.CRITICAL || severity == FraudSeverity.HIGH;
    }
    
    public boolean requiresManualReview() {
        return severity == FraudSeverity.HIGH || 
               (severity == FraudSeverity.MEDIUM && riskScore >= 0.5);
    }
    
    public boolean shouldBlockTransaction() {
        return severity == FraudSeverity.CRITICAL || 
               (severity == FraudSeverity.HIGH && riskScore >= 0.8);
    }
    
    public boolean isFinancialCrime() {
        return fraudType == FraudType.MONEY_LAUNDERING || 
               fraudType == FraudType.TERRORIST_FINANCING ||
               Boolean.TRUE.equals(amlFlagged) ||
               Boolean.TRUE.equals(sanctionsHit);
    }
    
    public int getResponsePriority() {
        int basePriority = severity.getPriority();
        if (Boolean.TRUE.equals(requiresImmediateAction)) basePriority -= 1;
        if (amount.compareTo(new BigDecimal("10000")) > 0) basePriority -= 1;
        if (Boolean.TRUE.equals(repeatOffender)) basePriority -= 1;
        if (isFinancialCrime()) basePriority -= 2;
        return Math.max(1, basePriority);
    }
}