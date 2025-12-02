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
 * API Key Entity
 */
@Entity
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_api_keys_user_id", columnList = "user_id"),
    @Index(name = "idx_api_keys_key_hash", columnList = "key_hash", unique = true),
    @Index(name = "idx_api_keys_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "api_key_id")
    private UUID apiKeyId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "key_name", length = 255, nullable = false)
    private String keyName;

    @Column(name = "key_hash", nullable = false, unique = true, length = 512)
    private String keyHash;

    @Column(name = "key_prefix", length = 20)
    private String keyPrefix;

    @Column(name = "permissions", length = 1000)
    private String permissions;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "usage_count")
    @Builder.Default
    private Long usageCount = 0L;

    @Column(name = "rate_limit")
    private Integer rateLimit;
    
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;
    
    @Column(name = "revocation_reason")
    private String revocationReason;

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
