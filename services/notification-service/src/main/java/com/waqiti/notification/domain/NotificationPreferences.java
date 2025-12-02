package com.waqiti.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreferences {
    @Id
    private UUID userId;

    @Column(nullable = false)
    private boolean appNotificationsEnabled;

    @Column(nullable = false)
    private boolean emailNotificationsEnabled;

    @Column(nullable = false)
    private boolean smsNotificationsEnabled;

    @Column(nullable = false)
    private boolean pushNotificationsEnabled;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "category_preferences",
            joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "category")
    @Column(name = "enabled")
    private Map<String, Boolean> categoryPreferences = new HashMap<>();

    @Column(name = "quiet_hours_start")
    private Integer quietHoursStart;

    @Column(name = "quiet_hours_end")
    private Integer quietHoursEnd;

    @Column(name = "email")
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;

    @Setter
    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Creates default notification preferences for a user
     */
    public static NotificationPreferences createDefault(UUID userId) {
        NotificationPreferences preferences = new NotificationPreferences();
        preferences.userId = userId;
        preferences.appNotificationsEnabled = true;
        preferences.emailNotificationsEnabled = true;
        preferences.smsNotificationsEnabled = false;
        preferences.pushNotificationsEnabled = false;

        // Enable all categories by default
        for (NotificationCategory category : NotificationCategory.values()) {
            preferences.categoryPreferences.put(category.name(), true);
        }

        preferences.createdAt = LocalDateTime.now();
        preferences.updatedAt = LocalDateTime.now();

        return preferences;
    }

    /**
     * Updates the app notification setting
     */
    public void setAppNotificationsEnabled(boolean enabled) {
        this.appNotificationsEnabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the email notification setting
     */
    public void setEmailNotificationsEnabled(boolean enabled) {
        this.emailNotificationsEnabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the SMS notification setting
     */
    public void setSmsNotificationsEnabled(boolean enabled) {
        this.smsNotificationsEnabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the push notification setting
     */
    public void setPushNotificationsEnabled(boolean enabled) {
        this.pushNotificationsEnabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates a category preference
     */
    public void setCategoryPreference(String category, boolean enabled) {
        this.categoryPreferences.put(category, enabled);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates quiet hours
     */
    public void setQuietHours(Integer start, Integer end) {
        this.quietHoursStart = start;
        this.quietHoursEnd = end;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates contact information
     */
    public void updateContactInfo(String email, String phoneNumber, String deviceToken) {
        if (email != null) {
            this.email = email;
        }

        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }

        if (deviceToken != null) {
            this.deviceToken = deviceToken;
        }

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Checks if notifications should be sent based on category and type
     */
    public boolean shouldSendNotification(String category, NotificationType type) {
        // Check category preference
        Boolean categoryEnabled = categoryPreferences.get(category);
        if (categoryEnabled == null || !categoryEnabled) {
            return false;
        }

        // Check type preference
        switch (type) {
            case APP -> {
                return appNotificationsEnabled;
            }
            case EMAIL -> {
                return emailNotificationsEnabled && email != null && !email.isEmpty();
            }
            case SMS -> {
                return smsNotificationsEnabled && phoneNumber != null && !phoneNumber.isEmpty();
            }
            case PUSH -> {
                return pushNotificationsEnabled && deviceToken != null && !deviceToken.isEmpty();
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Checks if current time is within quiet hours
     */
    public boolean isQuietHours() {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }

        int currentHour = LocalDateTime.now().getHour();

        if (quietHoursStart <= quietHoursEnd) {
            // E.g., 22:00 - 06:00
            return currentHour >= quietHoursStart && currentHour < quietHoursEnd;
        } else {
            // E.g., 22:00 - 06:00 (spans midnight)
            return currentHour >= quietHoursStart || currentHour < quietHoursEnd;
        }
    }
}