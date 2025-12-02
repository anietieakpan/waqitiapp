package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Fee Tier Entity
 * 
 * Represents different tiers within a fee schedule for tiered pricing.
 * For example, first 5 transactions free, next 10 at $1 each, etc.
 */
@Entity
@Table(name = "fee_tiers", indexes = {
    @Index(name = "idx_fee_tier_schedule", columnList = "feeScheduleId"),
    @Index(name = "idx_fee_tier_order", columnList = "feeScheduleId, tierOrder")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_schedule_id", nullable = false)
    private FeeSchedule feeSchedule;

    @Column(name = "fee_schedule_id", insertable = false, updatable = false)
    private UUID feeScheduleId;

    @Column(name = "tier_order", nullable = false)
    private Integer tierOrder;

    @Column(name = "tier_name", length = 100)
    private String tierName;

    @Column(name = "range_from", precision = 19, scale = 4)
    private BigDecimal rangeFrom;

    @Column(name = "range_to", precision = 19, scale = 4)
    private BigDecimal rangeTo;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "fee_percentage", precision = 8, scale = 6)
    private BigDecimal feePercentage;

    @Column(name = "free_quantity")
    private Integer freeQuantity;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Checks if an amount falls within this tier's range
     */
    public boolean isInRange(BigDecimal amount) {
        boolean aboveMin = rangeFrom == null || amount.compareTo(rangeFrom) >= 0;
        boolean belowMax = rangeTo == null || amount.compareTo(rangeTo) <= 0;
        return aboveMin && belowMax;
    }

    /**
     * Calculates fee for this tier based on amount
     */
    public BigDecimal calculateFee(BigDecimal amount, Integer transactionCount) {
        // Check if within free quantity
        if (freeQuantity != null && transactionCount != null && transactionCount <= freeQuantity) {
            return BigDecimal.ZERO;
        }

        BigDecimal fee = BigDecimal.ZERO;
        
        if (feeAmount != null) {
            fee = fee.add(feeAmount);
        }
        
        if (feePercentage != null && amount != null) {
            BigDecimal percentageFee = amount.multiply(feePercentage)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            fee = fee.add(percentageFee);
        }
        
        return fee;
    }
}