package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Card Activity Entity
 * Tracks all activities and actions performed on virtual cards
 */
@Entity
@Table(name = "card_activities", 
    indexes = {
        @Index(name = "idx_card_id", columnList = "card_id"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_action", columnList = "action"),
        @Index(name = "idx_timestamp", columnList = "timestamp"),
        @Index(name = "idx_activity_type", columnList = "activity_type"),
        @Index(name = "idx_status", columnList = "status")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CardActivity {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "action", nullable = false, length = 100)
    private String action;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private ActivityType activityType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivityStatus status;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "performed_by", length = 100)
    private String performedBy;
    
    @Column(name = "performed_by_role", length = 50)
    private String performedByRole;
    
    @Column(name = "source_ip", length = 45)
    private String sourceIp;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "device_id", length = 100)
    private String deviceId;
    
    @Column(name = "device_type", length = 50)
    private String deviceType;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
    
    @Column(name = "session_id", length = 100)
    private String sessionId;
    
    @Column(name = "correlation_id", length = 100)
    private String correlationId;
    
    @Column(name = "request_id", length = 100)
    private String requestId;
    
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "merchant_name", length = 200)
    private String merchantName;
    
    @Column(name = "merchant_id", length = 50)
    private String merchantId;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;
    
    @Column(name = "reason", length = 500)
    private String reason;
    
    @Column(name = "error_code", length = 50)
    private String errorCode;
    
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "fraud_score")
    private Integer fraudScore;
    
    @Column(name = "is_suspicious", nullable = false)
    private boolean isSuspicious;
    
    @Column(name = "is_automated", nullable = false)
    private boolean isAutomated;
    
    @Column(name = "is_system_action", nullable = false)
    private boolean isSystemAction;
    
    @Column(name = "requires_review", nullable = false)
    private boolean requiresReview;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
    
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    
    @Column(name = "duration_ms")
    private Long durationMs;
    
    @Column(name = "api_endpoint", length = 200)
    private String apiEndpoint;
    
    @Column(name = "http_method", length = 10)
    private String httpMethod;
    
    @Column(name = "response_code")
    private Integer responseCode;
    
    @ElementCollection
    @CollectionTable(
        name = "card_activity_metadata",
        joinColumns = @JoinColumn(name = "activity_id")
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * Activity types
     */
    public enum ActivityType {
        CARD_CREATION,
        CARD_ACTIVATION,
        CARD_DEACTIVATION,
        CARD_LOCK,
        CARD_UNLOCK,
        CARD_TERMINATION,
        CARD_REPLACEMENT,
        TRANSACTION,
        LIMIT_UPDATE,
        CONTROL_UPDATE,
        RESTRICTION_UPDATE,
        CVV_ROTATION,
        PIN_CHANGE,
        SETTINGS_UPDATE,
        AUTHENTICATION,
        AUTHORIZATION,
        DECLINE,
        REVERSAL,
        REFUND,
        DISPUTE,
        CHARGEBACK,
        SECURITY_CHECK,
        FRAUD_ALERT,
        COMPLIANCE_CHECK,
        AUDIT,
        SYSTEM_EVENT,
        ERROR,
        WARNING,
        INFO
    }
    
    /**
     * Activity status
     */
    public enum ActivityStatus {
        SUCCESS,
        FAILED,
        PENDING,
        IN_PROGRESS,
        CANCELLED,
        TIMEOUT,
        ERROR,
        WARNING,
        INFO
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = ActivityStatus.SUCCESS;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if activity is high risk
     */
    public boolean isHighRisk() {
        return (riskScore != null && riskScore > 70) ||
               (fraudScore != null && fraudScore > 50) ||
               isSuspicious ||
               activityType == ActivityType.FRAUD_ALERT ||
               activityType == ActivityType.SECURITY_CHECK;
    }
    
    /**
     * Check if activity needs investigation
     */
    public boolean needsInvestigation() {
        return requiresReview ||
               status == ActivityStatus.FAILED ||
               status == ActivityStatus.ERROR ||
               isHighRisk() ||
               (errorCode != null && !errorCode.isEmpty());
    }
    
    /**
     * Check if this is a transaction activity
     */
    public boolean isTransactionActivity() {
        return activityType == ActivityType.TRANSACTION ||
               activityType == ActivityType.AUTHORIZATION ||
               activityType == ActivityType.DECLINE ||
               activityType == ActivityType.REVERSAL ||
               activityType == ActivityType.REFUND;
    }
    
    /**
     * Check if this is a security activity
     */
    public boolean isSecurityActivity() {
        return activityType == ActivityType.CARD_LOCK ||
               activityType == ActivityType.CARD_UNLOCK ||
               activityType == ActivityType.CVV_ROTATION ||
               activityType == ActivityType.PIN_CHANGE ||
               activityType == ActivityType.SECURITY_CHECK ||
               activityType == ActivityType.FRAUD_ALERT;
    }
    
    /**
     * Check if this is an administrative activity
     */
    public boolean isAdministrativeActivity() {
        return activityType == ActivityType.LIMIT_UPDATE ||
               activityType == ActivityType.CONTROL_UPDATE ||
               activityType == ActivityType.RESTRICTION_UPDATE ||
               activityType == ActivityType.SETTINGS_UPDATE ||
               activityType == ActivityType.COMPLIANCE_CHECK ||
               activityType == ActivityType.AUDIT;
    }
    
    /**
     * Get activity severity
     */
    public ActivitySeverity getSeverity() {
        if (status == ActivityStatus.ERROR || status == ActivityStatus.FAILED) {
            return ActivitySeverity.HIGH;
        }
        if (status == ActivityStatus.WARNING || requiresReview) {
            return ActivitySeverity.MEDIUM;
        }
        if (isHighRisk()) {
            return ActivitySeverity.HIGH;
        }
        return ActivitySeverity.LOW;
    }
    
    public enum ActivitySeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Create activity for card creation
     */
    public static CardActivity cardCreated(String cardId, String userId, String details) {
        return CardActivity.builder()
            .cardId(cardId)
            .userId(userId)
            .action("CARD_CREATED")
            .details(details)
            .activityType(ActivityType.CARD_CREATION)
            .status(ActivityStatus.SUCCESS)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create activity for transaction
     */
    public static CardActivity transaction(String cardId, String userId, String transactionId,
                                          BigDecimal amount, String currency, ActivityStatus status) {
        return CardActivity.builder()
            .cardId(cardId)
            .userId(userId)
            .action("TRANSACTION")
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .activityType(ActivityType.TRANSACTION)
            .status(status)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create activity for security event
     */
    public static CardActivity securityEvent(String cardId, String userId, String action, 
                                            String details, boolean suspicious) {
        return CardActivity.builder()
            .cardId(cardId)
            .userId(userId)
            .action(action)
            .details(details)
            .activityType(ActivityType.SECURITY_CHECK)
            .status(ActivityStatus.WARNING)
            .isSuspicious(suspicious)
            .requiresReview(suspicious)
            .timestamp(LocalDateTime.now())
            .build();
    }
}