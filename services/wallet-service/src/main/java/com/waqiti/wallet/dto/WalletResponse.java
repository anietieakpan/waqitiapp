package com.waqiti.wallet.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet response DTO
 * Used to return wallet information to clients
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    
    private UUID walletId;
    private UUID userId;
    private String currency;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal reservedBalance;
    private String status;
    private String type;
    private WalletLimits limits;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isPrimary;
    private boolean isActive;
    private Map<String, Object> metadata;
    
    // Account information
    private String accountNumber;
    private String routingNumber;
    private String iban;
    private String swiftCode;
    
    // Security and compliance
    private String riskLevel;
    private boolean isFrozen;
    private String freezeReason;
    private List<String> restrictions;
    
    // KYC and verification status
    private String kycLevel;
    private boolean isVerified;
    private String verificationStatus;
    
    // Additional features
    private boolean supportsInstantTransfers;
    private boolean supportsInternationalTransfers;
    private boolean supportsCrypto;
    private List<String> supportedPaymentMethods;
    
    // Rate limiting and security
    private Integer dailyTransactionCount;
    private BigDecimal dailyTransactionAmount;
    private LocalDateTime lastTransactionAt;
    
    // Fees and charges
    private Map<String, BigDecimal> feeStructure;
    private BigDecimal monthlyMaintenanceFee;
    private boolean feeWaived;
    
    // Linked accounts and cards
    private List<UUID> linkedBankAccounts;
    private List<UUID> linkedCards;
    private List<UUID> linkedCryptoAddresses;
    
    // Notifications and preferences
    private Map<String, Boolean> notificationPreferences;
    private String preferredCurrency;
    private String timezone;
    
    // Audit information
    private String createdBy;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedAt;
    private String version;
    
    // SECURITY FIX: Timing attack protection fields
    private boolean dataStaleness;
    private String stalenessReason;
    private LocalDateTime lastSyncAttempt;
    
    // Additional computed fields
    public BigDecimal getTotalBalance() {
        return balance != null ? balance : BigDecimal.ZERO;
    }
    
    public boolean hasInsufficientFunds(BigDecimal amount) {
        return availableBalance == null || availableBalance.compareTo(amount) < 0;
    }
    
    public boolean isWithinDailyLimit(BigDecimal amount) {
        if (limits == null || limits.getDailyTransactionLimit() == null) {
            return true;
        }
        
        BigDecimal currentDailyAmount = dailyTransactionAmount != null ? dailyTransactionAmount : BigDecimal.ZERO;
        return currentDailyAmount.add(amount).compareTo(limits.getDailyTransactionLimit()) <= 0;
    }
    
    public boolean canPerformTransaction(BigDecimal amount, String transactionType) {
        if (!isActive || isFrozen) {
            return false;
        }
        
        if (hasInsufficientFunds(amount)) {
            return false;
        }
        
        if (!isWithinDailyLimit(amount)) {
            return false;
        }
        
        // Additional transaction type specific checks can be added here
        return true;
    }
}