package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Merchant insight model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantInsight {
    private String merchantName;
    private String merchantCategory;
    private BigDecimal totalSpent;
    private Integer transactionCount;
    private BigDecimal averageAmount;
    private LocalDateTime firstTransactionDate;
    private LocalDateTime lastTransactionDate;
    private String frequency; // DAILY, WEEKLY, MONTHLY, OCCASIONAL
}