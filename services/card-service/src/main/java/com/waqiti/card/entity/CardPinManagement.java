package com.waqiti.card.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardPinManagement entity - PIN management record
 * Represents PIN change history and security events for cards
 *
 * Tracks:
 * - PIN changes
 * - PIN verification attempts
 * - PIN lockouts
 * - PIN security events
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Entity
@Table(name = "card_pin_management", indexes = {
    @Index(name = "idx_pin_mgmt_id", columnList = "pin_event_id"),
    @Index(name = "idx_pin_mgmt_card", columnList = "card_id"),
    @Index(name = "idx_pin_mgmt_user", columnList = "user_id"),
    @Index(name = "idx_pin_mgmt_event_type", columnList = "event_type"),
    @Index(name = "idx_pin_mgmt_event_date", columnList = "event_date"),
    @Index(name = "idx_pin_mgmt_deleted", columnList = "deleted_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CardPinManagement extends BaseAuditEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ========================================================================
    // EVENT IDENTIFICATION
    // ========================================================================

    @Column(name = "pin_event_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "PIN event ID is required")
    private String pinEventId;

    @Column(name = "event_type", nullable = false, length = 50)
    @NotBlank(message = "Event type is required")
    private String eventType;

    @Column(name = "event_date", nullable = false)
    @NotNull
    @Builder.Default
    private LocalDateTime eventDate = LocalDateTime.now();

    // ========================================================================
    // REFERENCES
    // ========================================================================

    @Column(name = "card_id", nullable = false)
    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;

    // ========================================================================
    // PIN CHANGE EVENTS
    // ========================================================================

    @Column(name = "previous_pin_hash", length = 255)
    @Size(max = 255)
    private String previousPinHash;

    @Column(name = "new_pin_hash", length = 255)
    @Size(max = 255)
    private String newPinHash;

    @Column(name = "pin_change_reason", length = 100)
    @Size(max = 100)
    private String pinChangeReason;

    @Column(name = "change_requested_by", length = 100)
    @Size(max = 100)
    private String changeRequestedBy;

    @Column(name = "is_forced_change")
    @Builder.Default
    private Boolean isForcedChange = false;

    // ========================================================================
    // PIN VERIFICATION EVENTS
    // ========================================================================

    @Column(name = "verification_successful")
    private Boolean verificationSuccessful;

    @Column(name = "failed_attempts_count")
    @Min(0)
    @Builder.Default
    private Integer failedAttemptsCount = 0;

    @Column(name = "total_attempts")
    @Min(0)
    @Builder.Default
    private Integer totalAttempts = 0;

    @Column(name = "verification_channel", length = 50)
    @Size(max = 50)
    private String verificationChannel;

    // ========================================================================
    // PIN LOCK EVENTS
    // ========================================================================

    @Column(name = "is_lockout_event")
    @Builder.Default
    private Boolean isLockoutEvent = false;

    @Column(name = "lockout_duration_minutes")
    @Min(0)
    private Integer lockoutDurationMinutes;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "unlock_date")
    private LocalDateTime unlockDate;

    @Column(name = "unlocked_by", length = 100)
    @Size(max = 100)
    private String unlockedBy;

    @Column(name = "unlock_reason", length = 255)
    @Size(max = 255)
    private String unlockReason;

    // ========================================================================
    // LOCATION & DEVICE INFO
    // ========================================================================

    @Column(name = "ip_address", length = 45)
    @Size(max = 45)
    private String ipAddress;

    @Column(name = "device_id", length = 100)
    @Size(max = 100)
    private String deviceId;

    @Column(name = "device_type", length = 50)
    @Size(max = 50)
    private String deviceType;

    @Column(name = "location_latitude", precision = 10, scale = 7)
    private java.math.BigDecimal locationLatitude;

    @Column(name = "location_longitude", precision = 10, scale = 7)
    private java.math.BigDecimal locationLongitude;

    @Column(name = "location_city", length = 100)
    @Size(max = 100)
    private String locationCity;

    @Column(name = "location_country", length = 3)
    @Size(min = 2, max = 3)
    private String locationCountry;

    // ========================================================================
    // SECURITY CONTEXT
    // ========================================================================

    @Column(name = "terminal_id", length = 50)
    @Size(max = 50)
    private String terminalId;

    @Column(name = "merchant_id", length = 100)
    @Size(max = 100)
    private String merchantId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "is_suspicious")
    @Builder.Default
    private Boolean isSuspicious = false;

    @Column(name = "fraud_score", precision = 5, scale = 2)
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private java.math.BigDecimal fraudScore;

    @Column(name = "risk_indicators", columnDefinition = "TEXT")
    private String riskIndicators;

    // ========================================================================
    // DELIVERY METHOD (for PIN mailer/SMS)
    // ========================================================================

    @Column(name = "delivery_method", length = 30)
    @Size(max = 30)
    private String deliveryMethod;

    @Column(name = "delivery_address", length = 255)
    @Size(max = 255)
    private String deliveryAddress;

    @Column(name = "delivery_status", length = 30)
    @Size(max = 30)
    private String deliveryStatus;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "tracking_number", length = 100)
    @Size(max = 100)
    private String trackingNumber;

    // ========================================================================
    // PIN RESET
    // ========================================================================

    @Column(name = "is_reset_event")
    @Builder.Default
    private Boolean isResetEvent = false;

    @Column(name = "reset_method", length = 50)
    @Size(max = 50)
    private String resetMethod;

    @Column(name = "reset_token_hash", length = 255)
    @Size(max = 255)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    @Column(name = "reset_completed")
    @Builder.Default
    private Boolean resetCompleted = false;

    // ========================================================================
    // COMPLIANCE & AUDIT
    // ========================================================================

    @Column(name = "compliance_officer_notified")
    @Builder.Default
    private Boolean complianceOfficerNotified = false;

    @Column(name = "security_team_notified")
    @Builder.Default
    private Boolean securityTeamNotified = false;

    @Column(name = "user_notified")
    @Builder.Default
    private Boolean userNotified = false;

    @Column(name = "notification_channel", length = 50)
    @Size(max = 50)
    private String notificationChannel;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    // ========================================================================
    // METADATA
    // ========================================================================

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_data", columnDefinition = "jsonb")
    private Map<String, Object> eventData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if this is a security event
     */
    @Transient
    public boolean isSecurityEvent() {
        return isSuspicious ||
               isLockoutEvent ||
               (failedAttemptsCount != null && failedAttemptsCount >= 3) ||
               (fraudScore != null && fraudScore.compareTo(new java.math.BigDecimal("75.00")) > 0);
    }

    /**
     * Check if PIN is currently locked
     */
    @Transient
    public boolean isPinCurrentlyLocked() {
        if (lockedUntil == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(lockedUntil) && unlockDate == null;
    }

    /**
     * Check if this is a PIN change event
     */
    @Transient
    public boolean isPinChangeEvent() {
        return "PIN_CHANGE".equals(eventType) ||
               "PIN_SET".equals(eventType) ||
               "PIN_UPDATE".equals(eventType);
    }

    /**
     * Check if this is a verification event
     */
    @Transient
    public boolean isVerificationEvent() {
        return "PIN_VERIFICATION".equals(eventType) ||
               "PIN_VALIDATION".equals(eventType);
    }

    /**
     * Record successful verification
     */
    public void recordSuccessfulVerification(String channel) {
        this.eventType = "PIN_VERIFICATION";
        this.verificationSuccessful = true;
        this.totalAttempts = (this.totalAttempts == null ? 0 : this.totalAttempts) + 1;
        this.verificationChannel = channel;
    }

    /**
     * Record failed verification
     */
    public void recordFailedVerification(String channel) {
        this.eventType = "PIN_VERIFICATION";
        this.verificationSuccessful = false;
        this.failedAttemptsCount = (this.failedAttemptsCount == null ? 0 : this.failedAttemptsCount) + 1;
        this.totalAttempts = (this.totalAttempts == null ? 0 : this.totalAttempts) + 1;
        this.verificationChannel = channel;

        // Check if lockout threshold reached (3 failed attempts)
        if (this.failedAttemptsCount >= 3) {
            recordLockout(24 * 60); // Lock for 24 hours
        }
    }

    /**
     * Record PIN lockout
     */
    public void recordLockout(int durationMinutes) {
        this.isLockoutEvent = true;
        this.lockoutDurationMinutes = durationMinutes;
        this.lockedUntil = LocalDateTime.now().plusMinutes(durationMinutes);
    }

    /**
     * Record PIN unlock
     */
    public void recordUnlock(String unlockedBy, String reason) {
        this.unlockDate = LocalDateTime.now();
        this.unlockedBy = unlockedBy;
        this.unlockReason = reason;
    }

    /**
     * Record PIN change
     */
    public void recordPinChange(String previousHash, String newHash, String reason, boolean forced) {
        this.eventType = "PIN_CHANGE";
        this.previousPinHash = previousHash;
        this.newPinHash = newHash;
        this.pinChangeReason = reason;
        this.isForcedChange = forced;
    }

    /**
     * Record PIN reset
     */
    public void recordPinReset(String method, String tokenHash, LocalDateTime tokenExpiry) {
        this.eventType = "PIN_RESET";
        this.isResetEvent = true;
        this.resetMethod = method;
        this.resetTokenHash = tokenHash;
        this.resetTokenExpiresAt = tokenExpiry;
        this.resetCompleted = false;
    }

    /**
     * Mark reset as completed
     */
    public void completeReset() {
        this.resetCompleted = true;
    }

    /**
     * Notify user
     */
    public void notifyUser(String channel) {
        this.userNotified = true;
        this.notificationChannel = channel;
        this.notificationSentAt = LocalDateTime.now();
    }

    /**
     * Flag as suspicious
     */
    public void flagAsSuspicious(java.math.BigDecimal score, String indicators) {
        this.isSuspicious = true;
        this.fraudScore = score;
        this.riskIndicators = indicators;
        this.securityTeamNotified = true;
    }

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (eventDate == null) {
            eventDate = LocalDateTime.now();
        }
        if (isForcedChange == null) {
            isForcedChange = false;
        }
        if (failedAttemptsCount == null) {
            failedAttemptsCount = 0;
        }
        if (totalAttempts == null) {
            totalAttempts = 0;
        }
        if (isLockoutEvent == null) {
            isLockoutEvent = false;
        }
        if (isSuspicious == null) {
            isSuspicious = false;
        }
        if (isResetEvent == null) {
            isResetEvent = false;
        }
        if (resetCompleted == null) {
            resetCompleted = false;
        }
        if (complianceOfficerNotified == null) {
            complianceOfficerNotified = false;
        }
        if (securityTeamNotified == null) {
            securityTeamNotified = false;
        }
        if (userNotified == null) {
            userNotified = false;
        }
    }
}
