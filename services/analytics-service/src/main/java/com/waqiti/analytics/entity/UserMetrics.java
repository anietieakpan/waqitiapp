package com.waqiti.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User Metrics Entity
 *
 * Stores daily aggregated metrics for individual users.
 * Tracks spending behavior, transaction patterns, and engagement metrics.
 *
 * Updated by:
 * - AnalyticsService.updateUserMetrics()
 * - Daily batch aggregation jobs
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-10
 */
@Entity
@Table(name = "user_metrics", indexes = {
    @Index(name = "idx_user_metrics_user_date", columnList = "user_id, date", unique = true),
    @Index(name = "idx_user_metrics_date", columnList = "date"),
    @Index(name = "idx_user_metrics_transaction_count", columnList = "transaction_count")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class UserMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "transaction_count", nullable = false)
    @Builder.Default
    private Long transactionCount = 0L;

    @Column(name = "total_spent", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "average_transaction_value", precision = 19, scale = 4)
    private BigDecimal averageTransactionValue;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

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
