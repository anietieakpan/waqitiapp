package com.waqiti.payment.dto;

import com.waqiti.payment.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Detailed commission calculation result with full audit trail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionCalculation {
    
    private UUID calculationId;
    private BigDecimal transactionAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;
    private String currency;
    private TransactionType transactionType;
    private String appliedTier;
    private BigDecimal volumeDiscount;
    private BigDecimal promotionalDiscount;
    private Instant calculatedAt;
    private String calculationMethod;
    
    // Additional fields for audit and compliance
    private BigDecimal exchangeRate;
    private String baseCurrency;
    private BigDecimal baseCommissionAmount;
    private String rateSource;
    private UUID merchantId;
    private String merchantCategory;
    
    // Breakdown for transparency
    private BigDecimal baseCommission;
    private BigDecimal processingFee;
    private BigDecimal networkFee;
    private BigDecimal regulatoryFee;
    
    // Validation
    public boolean isValid() {
        return transactionAmount != null && 
               transactionAmount.compareTo(BigDecimal.ZERO) > 0 &&
               commissionAmount != null &&
               commissionAmount.compareTo(BigDecimal.ZERO) >= 0 &&
               netAmount != null &&
               netAmount.compareTo(BigDecimal.ZERO) >= 0 &&
               commissionRate != null &&
               commissionRate.compareTo(BigDecimal.ZERO) >= 0 &&
               currency != null && !currency.isEmpty();
    }
    
    /**
     * Calculates effective rate after all discounts.
     */
    public BigDecimal getEffectiveRate() {
        if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return commissionAmount.divide(transactionAmount, 6, RoundingMode.HALF_EVEN)
                               .multiply(new BigDecimal("100"));
    }
}