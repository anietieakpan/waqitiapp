package com.waqiti.analytics.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TransactionMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_transactions", nullable = false)
    private Long totalTransactions;

    @Column(name = "total_volume", precision = 19, scale = 4, nullable = false)
    private BigDecimal totalVolume;

    @Column(name = "successful_transactions", nullable = false)
    private Long successfulTransactions;

    @Column(name = "failed_transactions", nullable = false)
    private Long failedTransactions;

    @Column(name = "average_transaction_amount", precision = 19, scale = 4)
    private BigDecimal averageTransactionAmount;

    @Column(name = "peak_hour_volume", precision = 19, scale = 4)
    private BigDecimal peakHourVolume;

    @Column(name = "peak_hour")
    private Integer peakHour;

    @Column(name = "unique_users", nullable = false)
    private Long uniqueUsers;

    @Column(name = "new_users", nullable = false)
    private Long newUsers;

    @Column(name = "active_wallets", nullable = false)
    private Long activeWallets;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}