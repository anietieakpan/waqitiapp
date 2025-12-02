package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refund Record Entity
 * Tracks refund requests and processing status
 */
@Entity
@Table(name = "refund_records", indexes = {
    @Index(name = "idx_refund_wallet_id", columnList = "walletId"),
    @Index(name = "idx_refund_user_id", columnList = "userId"),
    @Index(name = "idx_refund_status", columnList = "status"),
    @Index(name = "idx_refund_correlation_id", columnList = "correlationId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID walletId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(length = 500)
    private String refundReason;

    @Column(length = 100)
    private String refundMethod; // BANK_TRANSFER, MOBILE_MONEY, etc.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(length = 100)
    private String correlationId;

    @Column(length = 100)
    private String externalRefundId; // External payment provider reference

    @Column(length = 1000)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @Column
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = RefundStatus.PENDING;
        }
    }
}
