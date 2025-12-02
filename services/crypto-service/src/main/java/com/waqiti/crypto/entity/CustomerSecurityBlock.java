package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Customer Security Block Entity
 * Tracks customer account blocks for regulatory compliance and security
 * Supports both temporary and permanent blocks with full audit trail
 */
@Entity
@Table(name = "customer_security_blocks", indexes = {
        @Index(name = "idx_security_block_customer_id", columnList = "customerId"),
        @Index(name = "idx_security_block_active", columnList = "active"),
        @Index(name = "idx_security_block_customer_active", columnList = "customerId,active"),
        @Index(name = "idx_security_block_correlation_id", columnList = "correlationId"),
        @Index(name = "idx_security_block_violation_type", columnList = "violationType"),
        @Index(name = "idx_security_block_expires_at", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CustomerSecurityBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 50)
    private String blockType; // PERMANENT, TEMPORARY

    @Column(nullable = false, length = 2000)
    private String blockReason;

    @Column(nullable = false, length = 100)
    private String violationType; // SANCTIONS_HIT, AML_VIOLATION, KYC_FAILURE, etc.

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private Instant blockedAt;

    @Column(nullable = false, length = 100)
    private String blockedBy;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false)
    private Boolean isPermanent;

    @Column
    private Instant expiresAt;

    @Column
    private Integer durationDays;

    @Column
    private Instant unblockedAt;

    @Column(length = 2000)
    private String unblockReason;

    @Column(length = 100)
    private String unblockedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;
}
