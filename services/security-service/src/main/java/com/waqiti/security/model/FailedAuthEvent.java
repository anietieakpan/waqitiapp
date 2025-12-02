package com.waqiti.security.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Failed Authentication Event Entity
 */
@Entity
@Table(name = "failed_auth_events",
    indexes = {
        @Index(name = "idx_failed_user_id", columnList = "user_id"),
        @Index(name = "idx_failed_ip_address", columnList = "ip_address"),
        @Index(name = "idx_failed_device_id", columnList = "device_id"),
        @Index(name = "idx_failed_at", columnList = "failed_at"),
        @Index(name = "idx_failed_reason", columnList = "failure_reason")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedAuthEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "failure_reason", nullable = false)
    private String failureReason;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "browser_name")
    private String browserName;

    @Column(name = "os_name")
    private String osName;

    @Column(name = "device_type")
    private String deviceType;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (failedAt == null) {
            failedAt = Instant.now();
        }
    }
}
