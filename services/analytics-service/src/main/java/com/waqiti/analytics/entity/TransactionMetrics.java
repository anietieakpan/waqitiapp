package com.waqiti.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Metrics Entity
 *
 * Stores individual transaction metrics for analytics processing.
 * Each record represents a single transaction's metrics.
 *
 * Used by:
 * - Real-time analytics
 * - Transaction analytics service
 * - PaymentAnalyticsEventConsumer
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Entity
@Table(name = "transaction_metrics", indexes = {
    @Index(name = "idx_transaction_metrics_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_transaction_metrics_user", columnList = "user_id, completed_at"),
    @Index(name = "idx_transaction_metrics_merchant", columnList = "merchant_id, completed_at"),
    @Index(name = "idx_transaction_metrics_status", columnList = "status, completed_at"),
    @Index(name = "idx_transaction_metrics_date", columnList = "completed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class TransactionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "channel", length = 20)
    private String channel;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "fraud_flag")
    @Builder.Default
    private Boolean fraudFlag = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;
}
