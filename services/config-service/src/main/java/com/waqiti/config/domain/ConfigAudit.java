package com.waqiti.config.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entity for configuration changes
 */
@Entity
@Table(name = "config_audit", indexes = {
    @Index(name = "idx_audit_config_key", columnList = "configKey"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_user", columnList = "userId"),
    @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String configKey;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(length = 100)
    private String userName;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(length = 100)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
