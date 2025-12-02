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
@Table(name = "deposit_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "daily_cash_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyCashLimit;

    @Column(name = "daily_check_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyCheckLimit;

    @Column(name = "per_deposit_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal perDepositLimit;

    @Column(name = "max_checks_per_deposit")
    private Integer maxChecksPerDeposit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
