package com.waqiti.customer.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Preference Entity
 *
 * Represents customer preferences for notifications, security settings,
 * statement delivery, and other configurable options.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_preference", indexes = {
    @Index(name = "idx_customer_preference_customer", columnList = "customer_id")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "id")
public class CustomerPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", unique = true, nullable = false, length = 100)
    private String customerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    // Notification Preferences
    @Column(name = "notification_email")
    @Builder.Default
    private Boolean notificationEmail = true;

    @Column(name = "notification_sms")
    @Builder.Default
    private Boolean notificationSms = true;

    @Column(name = "notification_push")
    @Builder.Default
    private Boolean notificationPush = true;

    @Column(name = "notification_in_app")
    @Builder.Default
    private Boolean notificationInApp = true;

    // Marketing Preferences
    @Column(name = "marketing_email")
    @Builder.Default
    private Boolean marketingEmail = false;

    @Column(name = "marketing_sms")
    @Builder.Default
    private Boolean marketingSms = false;

    // Statement Preferences
    @Enumerated(EnumType.STRING)
    @Column(name = "statement_delivery", length = 20)
    @Builder.Default
    private StatementDelivery statementDelivery = StatementDelivery.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_frequency", length = 20)
    @Builder.Default
    private StatementFrequency statementFrequency = StatementFrequency.MONTHLY;

    // Security Preferences
    @Column(name = "two_factor_enabled")
    @Builder.Default
    private Boolean twoFactorEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_method", length = 20)
    private TwoFactorMethod twoFactorMethod;

    @Column(name = "biometric_enabled")
    @Builder.Default
    private Boolean biometricEnabled = false;

    @Column(name = "session_timeout_minutes")
    @Builder.Default
    private Integer sessionTimeoutMinutes = 15;

    // Additional Preferences (JSONB)
    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> preferences = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum StatementDelivery {
        EMAIL,
        POSTAL_MAIL,
        ONLINE_ONLY,
        BOTH
    }

    public enum StatementFrequency {
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        ANNUALLY
    }

    public enum TwoFactorMethod {
        SMS,
        EMAIL,
        AUTHENTICATOR_APP,
        BIOMETRIC,
        HARDWARE_TOKEN
    }

    /**
     * Check if email notifications are enabled
     *
     * @return true if enabled
     */
    public boolean isNotificationEmailEnabled() {
        return notificationEmail != null && notificationEmail;
    }

    /**
     * Check if SMS notifications are enabled
     *
     * @return true if enabled
     */
    public boolean isNotificationSmsEnabled() {
        return notificationSms != null && notificationSms;
    }

    /**
     * Check if push notifications are enabled
     *
     * @return true if enabled
     */
    public boolean isNotificationPushEnabled() {
        return notificationPush != null && notificationPush;
    }

    /**
     * Check if in-app notifications are enabled
     *
     * @return true if enabled
     */
    public boolean isNotificationInAppEnabled() {
        return notificationInApp != null && notificationInApp;
    }

    /**
     * Check if marketing emails are enabled
     *
     * @return true if enabled
     */
    public boolean isMarketingEmailEnabled() {
        return marketingEmail != null && marketingEmail;
    }

    /**
     * Check if marketing SMS are enabled
     *
     * @return true if enabled
     */
    public boolean isMarketingSmsEnabled() {
        return marketingSms != null && marketingSms;
    }

    /**
     * Check if two-factor authentication is enabled
     *
     * @return true if enabled
     */
    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled != null && twoFactorEnabled;
    }

    /**
     * Check if biometric authentication is enabled
     *
     * @return true if enabled
     */
    public boolean isBiometricEnabled() {
        return biometricEnabled != null && biometricEnabled;
    }

    /**
     * Enable all notifications
     */
    public void enableAllNotifications() {
        this.notificationEmail = true;
        this.notificationSms = true;
        this.notificationPush = true;
        this.notificationInApp = true;
    }

    /**
     * Disable all notifications
     */
    public void disableAllNotifications() {
        this.notificationEmail = false;
        this.notificationSms = false;
        this.notificationPush = false;
        this.notificationInApp = false;
    }

    /**
     * Enable all marketing communications
     */
    public void enableAllMarketing() {
        this.marketingEmail = true;
        this.marketingSms = true;
    }

    /**
     * Disable all marketing communications
     */
    public void disableAllMarketing() {
        this.marketingEmail = false;
        this.marketingSms = false;
    }

    /**
     * Enable two-factor authentication
     *
     * @param method the two-factor method
     */
    public void enableTwoFactor(TwoFactorMethod method) {
        this.twoFactorEnabled = true;
        this.twoFactorMethod = method;
    }

    /**
     * Disable two-factor authentication
     */
    public void disableTwoFactor() {
        this.twoFactorEnabled = false;
        this.twoFactorMethod = null;
    }

    /**
     * Set custom preference
     *
     * @param key the preference key
     * @param value the preference value
     */
    public void setPreference(String key, Object value) {
        if (this.preferences == null) {
            this.preferences = new HashMap<>();
        }
        this.preferences.put(key, value);
    }

    /**
     * Get custom preference
     *
     * @param key the preference key
     * @return the preference value, or null if not found
     */
    public Object getPreference(String key) {
        if (this.preferences == null) {
            return null;
        }
        return this.preferences.get(key);
    }

    /**
     * Remove custom preference
     *
     * @param key the preference key
     */
    public void removePreference(String key) {
        if (this.preferences != null) {
            this.preferences.remove(key);
        }
    }
}
