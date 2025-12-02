package com.waqiti.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "device_tokens", indexes = {
    @Index(name = "idx_device_user_id", columnList = "user_id"),
    @Index(name = "idx_device_device_id", columnList = "device_id"),
    @Index(name = "idx_device_active", columnList = "active"),
    @Index(name = "idx_device_platform", columnList = "platform"),
    @Index(name = "idx_device_token", columnList = "token"),
    @Index(name = "idx_device_user_device", columnList = "user_id,device_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @Column(name = "token", nullable = false, length = 1000)
    private String token;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;
    
    @Column(name = "device_name", length = 255)
    private String deviceName;
    
    @Column(name = "device_model", length = 255)
    private String deviceModel;
    
    @Column(name = "app_version", length = 50)
    private String appVersion;
    
    @Column(name = "os_version", length = 50)
    private String osVersion;
    
    @Column(name = "manufacturer", length = 255)
    private String manufacturer;
    
    @Column(name = "timezone", length = 50)
    private String timezone;
    
    @Column(name = "language", length = 10)
    private String language;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "last_used")
    private LocalDateTime lastUsed;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;
    
    @OneToMany(mappedBy = "deviceToken", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TopicSubscription> topicSubscriptions = new ArrayList<>();
    
    /**
     * Platform enum for device types
     */
    public enum Platform {
        IOS("ios", "iOS"),
        ANDROID("android", "Android"),
        WEB("web", "Web Browser"),
        DESKTOP("desktop", "Desktop Application");
        
        private final String code;
        private final String displayName;
        
        Platform(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static Platform fromCode(String code) {
            for (Platform platform : values()) {
                if (platform.getCode().equals(code)) {
                    return platform;
                }
            }
            throw new IllegalArgumentException("Unknown platform code: " + code);
        }
        
        /**
         * Check if this platform supports push notifications
         */
        public boolean supportsPushNotifications() {
            return this != WEB; // Web uses different notification mechanism
        }
        
        /**
         * Get the Firebase/FCM platform identifier
         */
        public String getFcmPlatform() {
            switch (this) {
                case IOS:
                    return "apns";
                case ANDROID:
                    return "fcm";
                case WEB:
                    return "webpush";
                case DESKTOP:
                default:
                    return "fcm";
            }
        }
    }
    
    /**
     * Factory method to create a new device token
     */
    public static DeviceToken create(String userId, String token, Platform platform, String deviceId) {
        return DeviceToken.builder()
            .userId(userId)
            .token(token)
            .platform(platform)
            .deviceId(deviceId)
            .active(true)
            .lastUsed(LocalDateTime.now())
            .build();
    }
    
    /**
     * Update the token and mark as active
     */
    public void updateToken(String newToken) {
        this.token = newToken;
        this.active = true;
        this.lastUsed = LocalDateTime.now();
    }
    
    /**
     * Mark device token as inactive
     */
    public void deactivate() {
        this.active = false;
        this.invalidatedAt = LocalDateTime.now();
    }
    
    /**
     * Mark device token as active and update last used time
     */
    public void markAsUsed() {
        this.lastUsed = LocalDateTime.now();
        if (!this.active) {
            this.active = true;
            this.invalidatedAt = null;
        }
    }
    
    /**
     * Update device information
     */
    public void updateDeviceInfo(String deviceName, String deviceModel, String osVersion, String appVersion) {
        this.deviceName = deviceName;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
        this.appVersion = appVersion;
        markAsUsed();
    }
    
    /**
     * Check if this device token is expired (not used for over 30 days)
     */
    public boolean isExpired() {
        if (lastUsed == null) {
            return createdAt.isBefore(LocalDateTime.now().minusDays(30));
        }
        return lastUsed.isBefore(LocalDateTime.now().minusDays(30));
    }
    
    /**
     * Check if this device token should be considered stale (not used for over 7 days)
     */
    public boolean isStale() {
        if (lastUsed == null) {
            return createdAt.isBefore(LocalDateTime.now().minusDays(7));
        }
        return lastUsed.isBefore(LocalDateTime.now().minusDays(7));
    }
    
    /**
     * Get a safe representation of the token for logging (masked)
     */
    public String getMaskedToken() {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
    
    @PrePersist
    protected void onCreate() {
        if (lastUsed == null) {
            lastUsed = LocalDateTime.now();
        }
    }
}