package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLimits {
    
    private BigDecimal dailyTransferLimit;
    private BigDecimal singleTransferLimit;
    private BigDecimal monthlyTransferLimit;
    private BigDecimal dailyWithdrawalLimit;
    private BigDecimal singleWithdrawalLimit;
    
    private BigDecimal remainingDailyTransferLimit;
    private BigDecimal remainingMonthlyTransferLimit;
    private BigDecimal remainingDailyWithdrawalLimit;
    
    private String kycLevel;
    private boolean internationalTransfersEnabled;
    private boolean cryptoEnabled;
    private boolean instantTransfersEnabled;
    private boolean virtualCardsEnabled;
    
    private LocalDateTime limitsRefreshAt;
    private LocalDateTime lastUpdated;
}