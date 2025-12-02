package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Wallet Summary information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryDTO {
    
    private UUID walletId;
    private UUID userId;
    
    /**
     * Get wallet ID as string
     */
    public String getId() {
        return walletId != null ? walletId.toString() : null;
    }
    private String walletType;
    private String status;
    private String currency;
    
    // Balance information
    private BigDecimal availableBalance;
    private BigDecimal totalBalance;
    private BigDecimal pendingBalance;
    private BigDecimal frozenBalance;
    private BigDecimal creditLimit;
    private BigDecimal minBalance;
    private BigDecimal maxBalance;
    
    // Transaction statistics
    private Long totalTransactions;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpenses;
    
    // Recent activity
    private LocalDateTime lastTransactionDate;
    private BigDecimal lastTransactionAmount;
    private String lastTransactionType;
    private Integer recentTransactionCount;
    private List<RecentTransactionDTO> recentTransactions;
    
    // Account details
    private String accountNumber;
    private String accountName;
    private String bankName;
    private String routingNumber;
    private String swiftCode;
    
    // Security and compliance
    private String kycStatus;
    private String riskLevel;
    private boolean isVerified;
    private boolean isFrozen;
    private boolean isLocked;
    private LocalDateTime lastVerificationDate;
    
    // Feature flags
    private boolean canSendMoney;
    private boolean canReceiveMoney;
    private boolean canWithdraw;
    private boolean canDeposit;
    private boolean canExchange;
    
    // Connected accounts
    private List<ConnectedAccountDTO> connectedAccounts;
    private List<PaymentMethodDTO> paymentMethods;
    
    // Limits and thresholds
    private BigDecimal dailyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;
    private BigDecimal yearlyTransactionLimit;
    private BigDecimal dailyWithdrawalLimit;
    private BigDecimal monthlyWithdrawalLimit;
    
    // Usage statistics
    private BigDecimal dailySpent;
    private BigDecimal monthlySpent;
    private BigDecimal yearlySpent;
    private BigDecimal dailyWithdrawn;
    private BigDecimal monthlyWithdrawn;
    
    // Notifications
    private boolean lowBalanceAlert;
    private boolean transactionAlerts;
    private boolean securityAlerts;
    private BigDecimal lowBalanceThreshold;
    
    // Audit information
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdatedAt;
    private String createdBy;
    private String lastUpdatedBy;
    private Map<String, Object> metadata;
    
    /**
     * Wallet types
     */
    public enum WalletType {
        PERSONAL,
        BUSINESS,
        SAVINGS,
        CHECKING,
        PREPAID,
        CREDIT,
        ESCROW
    }
    
    /**
     * Wallet statuses
     */
    public enum Status {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        CLOSED,
        PENDING_VERIFICATION,
        RESTRICTED
    }
    
    /**
     * Check if wallet is active
     */
    public boolean isActive() {
        return Status.ACTIVE.name().equals(status);
    }
    
    /**
     * Check if wallet has sufficient balance for amount
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return availableBalance != null && availableBalance.compareTo(amount) >= 0;
    }
    
    /**
     * Check if wallet is near credit limit
     */
    public boolean isNearCreditLimit() {
        if (creditLimit == null || totalBalance == null) {
            return false;
        }
        BigDecimal threshold = creditLimit.multiply(new BigDecimal("0.8")); // 80% threshold
        return totalBalance.compareTo(threshold) >= 0;
    }
    
    /**
     * Check if wallet has low balance
     */
    public boolean hasLowBalance() {
        if (lowBalanceThreshold == null || availableBalance == null) {
            return false;
        }
        return availableBalance.compareTo(lowBalanceThreshold) <= 0;
    }
    
    /**
     * Get available credit
     */
    public BigDecimal getAvailableCredit() {
        if (creditLimit == null || totalBalance == null) {
            return BigDecimal.ZERO;
        }
        return creditLimit.subtract(totalBalance).max(BigDecimal.ZERO);
    }
    
    /**
     * Calculate utilization percentage
     */
    public Double getUtilizationPercentage() {
        if (creditLimit == null || creditLimit.equals(BigDecimal.ZERO) || totalBalance == null) {
            return 0.0;
        }
        return totalBalance.divide(creditLimit, 4, RoundingMode.HALF_UP)
                           .multiply(new BigDecimal("100"))
                           .doubleValue();
    }
    
    /**
     * Get remaining daily limit
     */
    public BigDecimal getRemainingDailyLimit() {
        if (dailyTransactionLimit == null || dailySpent == null) {
            return dailyTransactionLimit != null ? dailyTransactionLimit : BigDecimal.ZERO;
        }
        return dailyTransactionLimit.subtract(dailySpent).max(BigDecimal.ZERO);
    }
    
    /**
     * Get remaining monthly limit
     */
    public BigDecimal getRemainingMonthlyLimit() {
        if (monthlyTransactionLimit == null || monthlySpent == null) {
            return monthlyTransactionLimit != null ? monthlyTransactionLimit : BigDecimal.ZERO;
        }
        return monthlyTransactionLimit.subtract(monthlySpent).max(BigDecimal.ZERO);
    }
    
    /**
     * Check if wallet can perform transaction
     */
    public boolean canPerformTransaction(BigDecimal amount, String transactionType) {
        if (!isActive() || isFrozen || isLocked) {
            return false;
        }
        
        if ("SEND".equals(transactionType) && !canSendMoney) {
            return false;
        }
        
        if ("WITHDRAW".equals(transactionType) && !canWithdraw) {
            return false;
        }
        
        return hasSufficientBalance(amount);
    }
}