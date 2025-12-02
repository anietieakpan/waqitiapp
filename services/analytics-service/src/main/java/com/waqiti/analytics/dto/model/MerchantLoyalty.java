package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Merchant Loyalty DTO for analyzing customer loyalty to specific merchants
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantLoyalty {
    private String merchantName;
    private Long visitCount;
    private BigDecimal totalSpent;
    private BigDecimal averageDaysBetweenVisits;
    private String loyaltyLevel; // VERY_LOYAL, LOYAL, REGULAR, OCCASIONAL
    private LocalDateTime lastVisit;
    private LocalDateTime firstVisit;
    private BigDecimal averageSpendPerVisit;
}
