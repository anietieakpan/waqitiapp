package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLimits {
    
    private BigDecimal dailyLimit;
    private BigDecimal transactionLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal remainingDailyLimit;
    private BigDecimal remainingMonthlyLimit;
    private String level;
    private String currency;
    private boolean internationalTransfersAllowed;
    private boolean cryptoPurchaseAllowed;
    private boolean instantTransfersAllowed;
}