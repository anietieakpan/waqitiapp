package com.waqiti.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Notification Preference Entity
 * 
 * Stores user preferences for various notification types across different channels.
 * Supports granular control, quiet hours, frequency settings, and opt-in/opt-out tracking.
 * 
 * COMPLIANCE: Tracks opt-in/opt-out dates for regulatory requirements (GDPR, CAN-SPAM, TCPA)
 * AUDIT: All preference changes are versioned and logged
 */
@Entity
@Table(name = "notification_preferences", indexes = {
    @Index(name = "idx_notif_pref_user", columnList = "user_id"),
    @Index(name = "idx_notif_pref_type", columnList = "notification_type"),
    @Index(name = "idx_notif_pref_channel", columnList = "channel"),
    @Index(name = "idx_notif_pref_enabled", columnList = "enabled"),
    @Index(name = "idx_notif_pref_user_type_channel", 
           columnList = "user_id, notification_type, channel", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(nullable = false)
    private Long version;
    
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    
    @Column(name = "notification_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;
    
    @Column(name = "channel", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;
    
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;
    
    @Column(name = "frequency", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationFrequency frequency = NotificationFrequency.REAL_TIME;
    
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;
    
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;
    
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";
    
    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "en";
    
    @Column(name = "opt_in_date")
    private LocalDateTime optInDate;
    
    @Column(name = "opt_out_date")
    private LocalDateTime optOutDate;
    
    @Column(name = "opt_out_reason", columnDefinition = "TEXT")
    private String optOutReason;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum NotificationType {
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        PAYMENT_RECEIVED,
        PAYMENT_SENT,
        REFUND_PROCESSED,
        DEPOSIT_CONFIRMED,
        WITHDRAWAL_COMPLETED,
        BALANCE_LOW,
        LARGE_TRANSACTION,
        RECURRING_PAYMENT,
        
        SECURITY_ALERT,
        LOGIN_DETECTED,
        PASSWORD_CHANGED,
        TWO_FACTOR_ENABLED,
        SUSPICIOUS_ACTIVITY,
        ACCOUNT_LOCKED,
        
        KYC_REQUIRED,
        KYC_APPROVED,
        KYC_REJECTED,
        DOCUMENT_UPLOADED,
        VERIFICATION_PENDING,
        
        MARKETING,
        PRODUCT_UPDATE,
        FEATURE_ANNOUNCEMENT,
        PROMOTIONAL_OFFER,
        NEWSLETTER,
        
        SYSTEM_MAINTENANCE,
        SERVICE_OUTAGE,
        POLICY_UPDATE,
        TERMS_UPDATE,
        
        RECONCILIATION_REPORT,
        MONTHLY_STATEMENT,
        TAX_DOCUMENT,
        COMPLIANCE_REPORT
    }
    
    public enum NotificationChannel {
        EMAIL,
        SMS,
        PUSH,
        IN_APP,
        WEBHOOK
    }
    
    public enum NotificationFrequency {
        REAL_TIME,
        HOURLY,
        DAILY,
        WEEKLY,
        MONTHLY,
        NEVER
    }
    
    public boolean isInQuietHours(LocalTime currentTime) {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return currentTime.isAfter(quietHoursStart) && currentTime.isBefore(quietHoursEnd);
        } else {
            return currentTime.isAfter(quietHoursStart) || currentTime.isBefore(quietHoursEnd);
        }
    }
    
    public boolean shouldSendNotification(LocalTime currentTime) {
        return enabled && !isInQuietHours(currentTime);
    }
}