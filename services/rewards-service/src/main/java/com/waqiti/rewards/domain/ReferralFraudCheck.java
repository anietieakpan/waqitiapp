package com.waqiti.rewards.domain;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Referral Fraud Check Entity
 *
 * Records fraud detection checks performed on referrals
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Entity
@Table(name = "referral_fraud_checks", indexes = {
    @Index(name = "idx_fraud_checks_referral", columnList = "referralId"),
    @Index(name = "idx_fraud_checks_status", columnList = "checkStatus"),
    @Index(name = "idx_fraud_checks_review", columnList = "reviewDecision")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReferralFraudCheck {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Check ID is required")
    @Column(unique = true, nullable = false, length = 100)
    private String checkId;

    @NotBlank(message = "Referral ID is required")
    @Column(nullable = false, length = 100)
    private String referralId;

    // ============================================================================
    // CHECK DETAILS
    // ============================================================================

    @NotBlank(message = "Check type is required")
    @Column(nullable = false, length = 50)
    private String checkType; // DUPLICATE_IP, SELF_REFERRAL, VELOCITY, PATTERN_MATCH, DEVICE_FINGERPRINT

    @NotBlank(message = "Check status is required")
    @Column(nullable = false, length = 20)
    private String checkStatus; // PASSED, FAILED, SUSPICIOUS, REVIEW_REQUIRED

    /**
     * Risk score from 0.00 to 100.00
     * 0-20: Low risk
     * 21-50: Medium risk
     * 51-80: High risk
     * 81-100: Critical risk
     */
    @DecimalMin(value = "0.00", message = "Risk score must be between 0 and 100")
    @DecimalMax(value = "100.00", message = "Risk score must be between 0 and 100")
    @Column(precision = 5, scale = 2)
    private BigDecimal riskScore;

    // ============================================================================
    // DETECTION DETAILS
    // ============================================================================

    /**
     * Detailed fraud indicators
     * Example: {
     *   "duplicate_ip_count": 5,
     *   "same_device_referrals": 3,
     *   "velocity_exceeded": true,
     *   "suspicious_pattern": "rapid_signup"
     * }
     */
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> fraudIndicators = new HashMap<>();

    /**
     * Detection rules that were triggered
     */
    @ElementCollection
    @CollectionTable(name = "fraud_check_triggered_rules",
                     joinColumns = @JoinColumn(name = "fraud_check_id"))
    @Column(name = "rule_name")
    @Builder.Default
    private Set<String> detectionRulesTriggered = new HashSet<>();

    // ============================================================================
    // RESOLUTION
    // ============================================================================

    @Size(max = 100, message = "Reviewed by must not exceed 100 characters")
    @Column(length = 100)
    private String reviewedBy;

    @Column
    private LocalDateTime reviewedAt;

    @Size(max = 20, message = "Review decision must not exceed 20 characters")
    @Column(length = 20)
    private String reviewDecision; // APPROVED, REJECTED, PENDING

    @Column(columnDefinition = "TEXT")
    private String reviewNotes;

    // ============================================================================
    // ACTIONS TAKEN
    // ============================================================================

    @Size(max = 50, message = "Action taken must not exceed 50 characters")
    @Column(length = 50)
    private String actionTaken; // BLOCKED, FLAGGED, ALLOWED, MANUAL_REVIEW

    @Builder.Default
    @Column(nullable = false)
    private Boolean automatedAction = true;

    // ============================================================================
    // AUDIT FIELDS
    // ============================================================================

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ============================================================================
    // LIFECYCLE CALLBACKS
    // ============================================================================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ============================================================================
    // BUSINESS LOGIC METHODS
    // ============================================================================

    /**
     * Marks the check as reviewed
     */
    public void markAsReviewed(String reviewer, String decision, String notes) {
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewDecision = decision;
        this.reviewNotes = notes;
    }

    /**
     * Adds a fraud indicator
     */
    public void addFraudIndicator(String key, Object value) {
        if (this.fraudIndicators == null) {
            this.fraudIndicators = new HashMap<>();
        }
        this.fraudIndicators.put(key, value);
    }

    /**
     * Adds a triggered rule
     */
    public void addTriggeredRule(String ruleName) {
        if (this.detectionRulesTriggered == null) {
            this.detectionRulesTriggered = new HashSet<>();
        }
        this.detectionRulesTriggered.add(ruleName);
    }

    /**
     * Checks if the fraud check failed
     */
    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(checkStatus);
    }

    /**
     * Checks if the fraud check is suspicious
     */
    public boolean isSuspicious() {
        return "SUSPICIOUS".equalsIgnoreCase(checkStatus);
    }

    /**
     * Checks if the fraud check passed
     */
    public boolean isPassed() {
        return "PASSED".equalsIgnoreCase(checkStatus);
    }

    /**
     * Checks if manual review is required
     */
    public boolean requiresManualReview() {
        return "REVIEW_REQUIRED".equalsIgnoreCase(checkStatus) ||
               "PENDING".equalsIgnoreCase(reviewDecision);
    }

    /**
     * Checks if the check has been reviewed
     */
    public boolean isReviewed() {
        return reviewedAt != null;
    }

    /**
     * Gets risk level based on score
     */
    public String getRiskLevel() {
        if (riskScore == null) {
            return "UNKNOWN";
        }

        if (riskScore.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return "LOW";
        } else if (riskScore.compareTo(BigDecimal.valueOf(50)) <= 0) {
            return "MEDIUM";
        } else if (riskScore.compareTo(BigDecimal.valueOf(80)) <= 0) {
            return "HIGH";
        } else {
            return "CRITICAL";
        }
    }

    /**
     * Checks if the referral was approved after review
     */
    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(reviewDecision);
    }

    /**
     * Checks if the referral was rejected after review
     */
    public boolean isRejected() {
        return "REJECTED".equalsIgnoreCase(reviewDecision);
    }

    /**
     * Gets the number of fraud indicators detected
     */
    public int getFraudIndicatorCount() {
        return fraudIndicators != null ? fraudIndicators.size() : 0;
    }

    /**
     * Gets the number of detection rules triggered
     */
    public int getTriggeredRuleCount() {
        return detectionRulesTriggered != null ? detectionRulesTriggered.size() : 0;
    }

    /**
     * Checks if this is a high-risk check
     */
    public boolean isHighRisk() {
        String level = getRiskLevel();
        return "HIGH".equals(level) || "CRITICAL".equals(level);
    }

    /**
     * Gets time elapsed since check was created (in hours)
     */
    public long getHoursSinceCheck() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
    }

    /**
     * Gets time elapsed since review (in hours), null if not reviewed
     */
    public Long getHoursSinceReview() {
        if (reviewedAt == null) {
            return null;
        }
        return java.time.Duration.between(reviewedAt, LocalDateTime.now()).toHours();
    }
}
