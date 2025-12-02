package com.waqiti.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Authentication Event domain model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "authentication_events", indexes = {
    @Index(name = "idx_auth_event_user_id", columnList = "userId"),
    @Index(name = "idx_auth_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_auth_event_source_ip", columnList = "sourceIp")
})
public class AuthenticationEvent {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String userId;

    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthMethod authMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthResult authResult;

    @Column(nullable = false)
    private String sourceIp;

    @Column(length = 2000)
    private String userAgent;

    private String deviceId;

    private String deviceFingerprint;

    // Geolocation data
    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String isp;
    private String organization;

    // Application context
    private String applicationId;
    private String clientType;
    private String platform;

    // Browser information
    private String browserName;
    private String browserVersion;
    private String osName;
    private String osVersion;
    private String screenResolution;
    private String timezone;
    private String language;
    private boolean cookiesEnabled;
    private boolean javaScriptEnabled;

    @ElementCollection
    @CollectionTable(name = "auth_event_plugins", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "plugin")
    private List<String> pluginsInstalled;

    // Authentication details
    private Integer authAttempts;
    private Long timeToComplete;
    private String referrer;

    @ElementCollection
    @CollectionTable(name = "auth_event_http_headers", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value", length = 2000)
    private Map<String, Object> httpHeaders;

    @ElementCollection
    @CollectionTable(name = "auth_event_risk_flags", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "risk_flag")
    private List<String> riskFlags;

    private String sourceSystem;

    @ElementCollection
    @CollectionTable(name = "auth_event_metadata", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value", length = 2000)
    private Map<String, Object> metadata;

    @Column(nullable = false)
    private Instant timestamp;

    private Instant createdAt;

    // Enriched data (not persisted initially, added during processing)
    @Transient
    private String userRiskLevel;

    @Transient
    private Long accountAge;

    @Transient
    private Instant lastLoginTime;

    @Transient
    private LocationData lastLoginLocation;

    @Transient
    private List<Integer> typicalLoginTimes;

    @Transient
    private List<String> typicalDevices;

    @Transient
    private List<String> typicalLocations;

    @Transient
    private boolean knownDevice;

    @Transient
    private Integer deviceRiskScore;

    @Transient
    private Instant deviceFirstSeen;

    @Transient
    private Instant deviceLastSeen;

    @Transient
    private Integer recentAuthCount;

    @Transient
    private Integer recentFailedAuthCount;

    @Transient
    private Integer recentSuccessfulAuthCount;

    @Transient
    private Integer recentLocationCount;

    @Transient
    private Integer recentDeviceCount;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Alias method for getSourceIp() for backward compatibility
     * @return the source IP address
     */
    public String getIpAddress() {
        return this.sourceIp;
    }

    /**
     * Alias setter for setSourceIp() for backward compatibility
     * @param ipAddress the IP address to set
     */
    public void setIpAddress(String ipAddress) {
        this.sourceIp = ipAddress;
    }
}
