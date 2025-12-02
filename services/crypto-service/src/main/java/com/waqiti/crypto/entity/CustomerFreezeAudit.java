package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Freeze Audit Entity
 * Tracks customer-level transaction freezes for regulatory compliance
 */
@Entity
@Table(name = "customer_freeze_audits", indexes = {
        @Index(name = "idx_freeze_audit_customer_id", columnList = "customerId"),
        @Index(name = "idx_freeze_audit_correlation_id", columnList = "correlationId"),
        @Index(name = "idx_freeze_audit_frozen_at", columnList = "frozenAt"),
        @Index(name = "idx_freeze_audit_freeze_type", columnList = "freezeType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CustomerFreezeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false, length = 1000)
    private String freezeReason;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private Integer transactionsFrozen;

    @Column(nullable = false)
    private Integer transactionsFailed;

    @Column(nullable = false)
    private LocalDateTime frozenAt;

    @Column(nullable = false, length = 100)
    private String freezeType;

    @Column
    private LocalDateTime releasedAt;

    @Column(length = 1000)
    private String releaseReason;

    @Column
    private String releasedBy;

    @Column(nullable = false)
    private Boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;
}
