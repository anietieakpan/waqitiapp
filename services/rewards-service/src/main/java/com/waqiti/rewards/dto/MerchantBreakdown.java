package com.waqiti.rewards.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal; /**
 * Merchant breakdown class for analytics
 */
@Data
@Builder
public class MerchantBreakdown {
    private String merchantId;
    private String merchantName;
    private BigDecimal spending;
    private BigDecimal cashbackEarned;
    private Long transactionCount;
    private BigDecimal averageTransactionAmount;
    private BigDecimal effectiveRate;
}
