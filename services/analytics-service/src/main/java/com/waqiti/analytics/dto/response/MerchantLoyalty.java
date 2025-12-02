package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Merchant Loyalty DTO
 *
 * Represents loyalty and retention metrics for a merchant.
 *
 * @author Waqiti Analytics Team
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantLoyalty {

    private String merchantName;
    private BigDecimal loyaltyScore; // 0.0 - 1.0
    private Integer monthsAsCustomer;
    private BigDecimal lifetimeValue;
    private String loyaltyTier; // BRONZE, SILVER, GOLD, PLATINUM
}
