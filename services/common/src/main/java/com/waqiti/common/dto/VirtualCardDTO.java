package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Data Transfer Object for Virtual Card information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualCardDTO {
    
    private UUID cardId;
    private UUID userId;
    private UUID walletId;
    
    /**
     * Get card ID as string
     */
    public String getId() {
        return cardId != null ? cardId.toString() : null;
    }
    private String cardNumber; // Masked for security
    private String cardHolderName;
    private String cardType;
    private String cardBrand;
    private String status;
    private LocalDate expiryDate;
    private String cvv; // Masked for security
    
    // Balance and limits
    private BigDecimal balance;
    private BigDecimal creditLimit;
    private BigDecimal dailySpendLimit;
    private BigDecimal monthlySpendLimit;
    private BigDecimal transactionLimit;
    private BigDecimal atmWithdrawalLimit;
    private BigDecimal onlineSpendLimit;
    
    // Usage statistics
    private BigDecimal dailySpent;
    private BigDecimal monthlySpent;
    private BigDecimal yearlySpent;
    private BigDecimal totalSpent;
    private Long totalTransactions;
    private LocalDateTime lastUsedAt;
    private String lastUsedMerchant;
    private BigDecimal lastTransactionAmount;
    
    // Security features
    private boolean isLocked;
    private boolean isFrozen;
    private boolean canOnlineTransactions;
    private boolean canAtmWithdrawals;
    private boolean canContactlessPayments;
    private boolean canInternationalTransactions;
    private boolean requiresPinVerification;
    private boolean requires3dsVerification;
    
    // Geographic restrictions
    private Set<String> allowedCountries;
    private Set<String> blockedCountries;
    private Set<String> allowedMerchantCategories;
    private Set<String> blockedMerchantCategories;
    
    // Notification settings
    private boolean transactionAlerts;
    private boolean lowBalanceAlerts;
    private boolean securityAlerts;
    private boolean internationalUsageAlerts;
    private BigDecimal alertThreshold;
    
    // Virtual card specific
    private String cardProvider; // Visa, MasterCard, etc.
    private String issuerName;
    private String processorNetwork;
    private boolean isVirtual;
    private boolean isTemporary;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String purpose; // Single-use, multi-use, subscription, etc.
    
    // Subscription management
    private boolean canRecurringPayments;
    private Set<String> authorizedSubscriptions;
    private BigDecimal recurringPaymentLimit;
    
    // Merchant specific
    private String preferredMerchant;
    private Set<String> restrictedToMerchants;
    private boolean merchantLocked;
    
    // Audit information
    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private LocalDateTime lastModifiedAt;
    private String createdBy;
    private String lastModifiedBy;
    private Map<String, Object> metadata;
    
    // Fraud and compliance
    private String riskScore;
    private boolean isFlaggedForReview;
    private LocalDateTime lastSecurityCheck;
    private String complianceStatus;
    
    /**
     * Card types
     */
    public enum CardType {
        DEBIT,
        CREDIT,
        PREPAID,
        VIRTUAL,
        GIFT_CARD,
        CORPORATE
    }
    
    /**
     * Card statuses
     */
    public enum Status {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        EXPIRED,
        CANCELLED,
        PENDING_ACTIVATION,
        BLOCKED
    }
    
    /**
     * Card brands
     */
    public enum CardBrand {
        VISA,
        MASTERCARD,
        AMERICAN_EXPRESS,
        DISCOVER,
        UNIONPAY
    }
    
    /**
     * Check if card is active and usable
     */
    public boolean isUsable() {
        return Status.ACTIVE.name().equals(status) && 
               !isLocked && !isFrozen && 
               !isExpired();
    }
    
    /**
     * Check if card is expired
     */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }
    
    /**
     * Check if card can be used for amount
     */
    public boolean canSpend(BigDecimal amount) {
        if (!isUsable()) {
            return false;
        }
        
        // Check balance for debit/prepaid cards
        if (CardType.DEBIT.name().equals(cardType) || CardType.PREPAID.name().equals(cardType)) {
            return balance != null && balance.compareTo(amount) >= 0;
        }
        
        // Check credit limit for credit cards
        if (CardType.CREDIT.name().equals(cardType)) {
            BigDecimal availableCredit = getAvailableCredit();
            return availableCredit.compareTo(amount) >= 0;
        }
        
        return true;
    }
    
    /**
     * Get available credit for credit cards
     */
    public BigDecimal getAvailableCredit() {
        if (!CardType.CREDIT.name().equals(cardType) || creditLimit == null || balance == null) {
            return BigDecimal.ZERO;
        }
        return creditLimit.subtract(balance).max(BigDecimal.ZERO);
    }
    
    /**
     * Check daily spending limit
     */
    public boolean withinDailyLimit(BigDecimal amount) {
        if (dailySpendLimit == null) {
            return true;
        }
        BigDecimal remainingLimit = dailySpendLimit.subtract(dailySpent != null ? dailySpent : BigDecimal.ZERO);
        return remainingLimit.compareTo(amount) >= 0;
    }
    
    /**
     * Check monthly spending limit
     */
    public boolean withinMonthlyLimit(BigDecimal amount) {
        if (monthlySpendLimit == null) {
            return true;
        }
        BigDecimal remainingLimit = monthlySpendLimit.subtract(monthlySpent != null ? monthlySpent : BigDecimal.ZERO);
        return remainingLimit.compareTo(amount) >= 0;
    }
    
    /**
     * Check if transaction is allowed for country
     */
    public boolean isCountryAllowed(String countryCode) {
        if (allowedCountries != null && !allowedCountries.isEmpty()) {
            return allowedCountries.contains(countryCode);
        }
        if (blockedCountries != null) {
            return !blockedCountries.contains(countryCode);
        }
        return true;
    }
    
    /**
     * Check if merchant category is allowed
     */
    public boolean isMerchantCategoryAllowed(String merchantCategory) {
        if (allowedMerchantCategories != null && !allowedMerchantCategories.isEmpty()) {
            return allowedMerchantCategories.contains(merchantCategory);
        }
        if (blockedMerchantCategories != null) {
            return !blockedMerchantCategories.contains(merchantCategory);
        }
        return true;
    }
    
    /**
     * Get masked card number for display
     */
    public String getMaskedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        return "**** **** **** " + lastFour;
    }
    
    /**
     * Get remaining daily limit
     */
    public BigDecimal getRemainingDailyLimit() {
        if (dailySpendLimit == null) {
            return null;
        }
        return dailySpendLimit.subtract(dailySpent != null ? dailySpent : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }
    
    /**
     * Get remaining monthly limit
     */
    public BigDecimal getRemainingMonthlyLimit() {
        if (monthlySpendLimit == null) {
            return null;
        }
        return monthlySpendLimit.subtract(monthlySpent != null ? monthlySpent : BigDecimal.ZERO).max(BigDecimal.ZERO);
    }
}