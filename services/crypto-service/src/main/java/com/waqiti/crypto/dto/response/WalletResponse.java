/**
 * Wallet Response DTO
 * Response containing wallet information
 */
package com.waqiti.crypto.dto.response;

import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.WalletStatus;
import com.waqiti.crypto.entity.WalletType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    
    private UUID walletId;
    private UUID userId;
    private CryptoCurrency currency;
    private WalletType walletType;
    private WalletStatus status;
    private String walletName;
    private String primaryAddress;
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private Integer addressIndex;
    private boolean multiSigEnabled;
    private Integer requiredSignatures;
    private boolean whitelistEnabled;
    private boolean autoSweepEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private List<String> addresses;
    private SecurityFeatures securityFeatures;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityFeatures {
        private boolean twoFactorRequired;
        private boolean whitelistEnabled;
        private boolean withdrawalLimits;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private boolean fraudDetectionEnabled;
    }
}