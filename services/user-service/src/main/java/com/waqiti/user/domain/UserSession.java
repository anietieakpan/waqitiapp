package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Session Entity
 */
@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_user_sessions_session_token", columnList = "session_token"),
    @Index(name = "idx_user_sessions_status", columnList = "status"),
    @Index(name = "idx_user_sessions_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "session_token", nullable = false, unique = true, length = 512)
    private String sessionToken;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;
    
    @Column(name = "termination_reason")
    private String terminationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Version
    @Column(name = "version")
    private Long version;
}
