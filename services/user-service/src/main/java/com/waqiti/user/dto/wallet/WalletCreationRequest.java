package com.waqiti.user.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreationRequest {
    private String userId;
    private String email;
    private String currency;
    private String countryCode;
    private String walletType;
    private String tier;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
    private boolean enableNotifications;
    private boolean enableFraudProtection;
    private boolean requireMfaForHighValue;
    private Map<String, Object> metadata;
}