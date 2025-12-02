package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLimits {
    private Map<String, LimitInfo> dailyLimits;
    private Map<String, LimitInfo> monthlyLimits;
    private Map<String, LimitInfo> transactionLimits;
}
