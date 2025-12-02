package com.waqiti.atm.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "withdrawal_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "per_transaction_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal perTransactionLimit;

    @Column(name = "daily_transaction_count_limit")
    private Integer dailyTransactionCountLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
