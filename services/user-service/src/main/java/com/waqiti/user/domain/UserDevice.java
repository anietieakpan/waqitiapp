package com.waqiti.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * User Device Entity
 * 
 * Tracks devices used by users for authentication and security purposes
 */
@Entity
@Table(name = "user_devices", indexes = {
    @Index(name = "idx_device_user_id", columnList = "user_id"),
    @Index(name = "idx_device_fingerprint", columnList = "device_fingerprint"),
    @Index(name = "idx_device_trusted", columnList = "is_trusted"),
    @Index(name = "idx_device_last_used", columnList = "last_used_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "device_id", unique = true, nullable = false, length = 100)
    private String deviceId;
    
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    
    @Column(name = "device_name", length = 255)
    private String deviceName;
    
    @Column(name = "device_type", length = 50)
    private String deviceType; // MOBILE, DESKTOP, TABLET, OTHER
    
    @Column(name = "device_fingerprint", nullable = false, length = 255)
    private String deviceFingerprint;
    
    @Column(name = "fingerprint", length = 255)
    private String fingerprint; // Alias for deviceFingerprint
    
    // Device information
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "browser_name", length = 50)
    private String browserName;
    
    @Column(name = "browser_version", length = 50)
    private String browserVersion;
    
    @Column(name = "os_name", length = 50)
    private String osName;
    
    @Column(name = "os_version", length = 50)
    private String osVersion;
    
    @Column(name = "device_model", length = 100)
    private String deviceModel;
    
    @Column(name = "device_vendor", length = 100)
    private String deviceVendor;
    
    // Location information
    @Column(name = "last_ip_address", length = 50)
    private String lastIpAddress;
    
    @Column(name = "last_country", length = 50)
    private String lastCountry;
    
    @Column(name = "last_city", length = 100)
    private String lastCity;
    
    @Column(name = "last_latitude")
    private Double lastLatitude;
    
    @Column(name = "last_longitude")
    private Double lastLongitude;
    
    // Trust and security
    @Column(name = "is_trusted")
    private Boolean isTrusted = false;
    
    @Column(name = "trust_score")
    private Double trustScore = 0.0;
    
    @Column(name = "trust_level", length = 50)
    private String trustLevel; // UNTRUSTED, LOW, MEDIUM, HIGH, TRUSTED
    
    @Column(name = "is_verified")
    private Boolean isVerified = false;
    
    @Column(name = "risk_score")
    private Double riskScore = 0.0;
    
    @Column(name = "is_blocked")
    private Boolean isBlocked = false;
    
    @Column(name = "block_reason", length = 500)
    private String blockReason;
    
    // Authentication methods
    @Column(name = "supports_biometric")
    private Boolean supportsBiometric = false;
    
    @Column(name = "biometric_enabled")
    private Boolean biometricEnabled = false;
    
    @Column(name = "push_token", length = 500)
    private String pushToken;
    
    @Column(name = "push_enabled")
    private Boolean pushEnabled = false;
    
    // Usage statistics
    @Column(name = "login_count")
    private Integer loginCount = 0;
    
    @Column(name = "failed_login_count")
    private Integer failedLoginCount = 0;
    
    @Column(name = "suspicious_activity_count")
    private Integer suspiciousActivityCount = 0;
    
    // Additional metadata
    @Column(name = "metadata", columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> metadata = new HashMap<>();
    
    @Column(name = "device_info", columnDefinition = "TEXT")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> deviceInfo = new HashMap<>();
    
    // Timestamps
    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @Column(name = "last_seen")
    private LocalDateTime lastSeen; // Alias for lastUsedAt
    
    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;
    
    @Column(name = "trusted_at")
    private LocalDateTime trustedAt;
    
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        firstSeenAt = LocalDateTime.now();
        if (deviceId == null) {
            deviceId = "dev_" + java.util.UUID.randomUUID().toString();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Get last seen timestamp (returns lastSeen or lastUsedAt)
     */
    public LocalDateTime getLastSeen() {
        return lastSeen != null ? lastSeen : lastUsedAt;
    }
    
    /**
     * Set fingerprint and update deviceFingerprint
     */
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
        this.deviceFingerprint = fingerprint;
    }
    
    /**
     * Get device info
     */
    public Map<String, Object> getDeviceInfo() {
        return deviceInfo != null ? deviceInfo : new HashMap<>();
    }
    
    /**
     * Get first seen timestamp
     */
    public LocalDateTime getFirstSeen() {
        return firstSeenAt;
    }
    
    /**
     * Get trust level
     */
    public String getTrustLevel() {
        if (trustLevel != null) return trustLevel;
        if (trustScore >= 0.8) return "TRUSTED";
        if (trustScore >= 0.6) return "HIGH";
        if (trustScore >= 0.4) return "MEDIUM";
        if (trustScore >= 0.2) return "LOW";
        return "UNTRUSTED";
    }
    
    /**
     * Check if verified
     */
    public Boolean isVerified() {
        return Boolean.TRUE.equals(isVerified);
    }
    
    /**
     * JSON converter for Map fields
     */
    @Converter
    public static class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
        private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        @Override
        public String convertToDatabaseColumn(Map<String, Object> attribute) {
            try {
                return attribute == null ? null : objectMapper.writeValueAsString(attribute);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting map to JSON", e);
            }
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> convertToEntityAttribute(String dbData) {
            try {
                return dbData == null ? new HashMap<>() : objectMapper.readValue(dbData, Map.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting JSON to map", e);
            }
        }
    }
}