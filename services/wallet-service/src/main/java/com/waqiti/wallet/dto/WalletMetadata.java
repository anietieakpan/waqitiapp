package com.waqiti.wallet.dto;

import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.WalletType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet metadata data transfer object
 * Contains wallet configuration and status information for caching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletMetadata {
    
    private UUID walletId;
    private UUID userId;
    private WalletType walletType;
    private String currency;
    private WalletStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    private boolean isActive;
    private boolean isPrimary;
    
    // Limits
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal singleTransactionLimit;
    
    // Features
    private boolean allowInternationalTransfers;
    private boolean allowCryptocurrency;
    private boolean allowInstantDeposits;
    private boolean requireTwoFactorAuth;
    
    // Risk settings
    private String riskLevel;
    private boolean isMonitored;
    
    /**
     * Check if wallet is operational
     */
    public boolean isOperational() {
        return isActive && 
               status == WalletStatus.ACTIVE;
    }
    
    /**
     * Check if wallet supports a specific feature
     */
    public boolean supportsFeature(String feature) {
        switch (feature.toLowerCase()) {
            case "international":
                return allowInternationalTransfers;
            case "crypto":
                return allowCryptocurrency;
            case "instant_deposits":
                return allowInstantDeposits;
            case "2fa":
                return requireTwoFactorAuth;
            default:
                return false;
        }
    }
    
    /**
     * Check if amount is within limits
     */
    public boolean isWithinLimits(BigDecimal amount) {
        if (singleTransactionLimit != null && 
            amount.compareTo(singleTransactionLimit) > 0) {
            return false;
        }
        
        // Daily and monthly limits would need additional context
        // (current usage) which should be checked separately
        return true;
    }
}