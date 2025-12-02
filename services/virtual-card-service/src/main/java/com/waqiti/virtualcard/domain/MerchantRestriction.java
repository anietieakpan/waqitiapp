package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Merchant Restriction Entity
 * Manages merchant-specific restrictions for virtual cards
 */
@Entity
@Table(name = "merchant_restrictions", indexes = {
    @Index(name = "idx_card_id", columnList = "card_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MerchantRestriction {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false, unique = true)
    private String cardId;
    
    @ElementCollection
    @CollectionTable(
        name = "allowed_merchants",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "merchant_name")
    private List<String> allowedMerchants;
    
    @ElementCollection
    @CollectionTable(
        name = "blocked_merchants",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "merchant_name")
    private List<String> blockedMerchants;
    
    @ElementCollection
    @CollectionTable(
        name = "allowed_merchant_ids",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "merchant_id")
    private List<String> allowedMerchantIds;
    
    @ElementCollection
    @CollectionTable(
        name = "blocked_merchant_ids",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "merchant_id")
    private List<String> blockedMerchantIds;
    
    @ElementCollection
    @CollectionTable(
        name = "allowed_categories",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "category")
    private List<String> allowedCategories;
    
    @ElementCollection
    @CollectionTable(
        name = "blocked_categories",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "category")
    private List<String> blockedCategories;
    
    @ElementCollection
    @CollectionTable(
        name = "allowed_mccs",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "mcc")
    private List<String> allowedMccs;
    
    @ElementCollection
    @CollectionTable(
        name = "blocked_mccs",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @Column(name = "mcc")
    private List<String> blockedMccs;
    
    @ElementCollection
    @CollectionTable(
        name = "merchant_limits",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @MapKeyColumn(name = "merchant_id")
    @Column(name = "limit_amount")
    private Map<String, java.math.BigDecimal> merchantLimits;
    
    @ElementCollection
    @CollectionTable(
        name = "category_limits",
        joinColumns = @JoinColumn(name = "restriction_id")
    )
    @MapKeyColumn(name = "category")
    @Column(name = "limit_amount")
    private Map<String, java.math.BigDecimal> categoryLimits;
    
    @Column(name = "whitelist_mode", nullable = false)
    private boolean whitelistMode;
    
    @Column(name = "blacklist_mode", nullable = false)
    private boolean blacklistMode;
    
    @Column(name = "allow_unknown_merchants", nullable = false)
    private boolean allowUnknownMerchants;
    
    @Column(name = "require_approval_for_new_merchants", nullable = false)
    private boolean requireApprovalForNewMerchants;
    
    @Column(name = "auto_approve_trusted_merchants", nullable = false)
    private boolean autoApproveTrustedMerchants;
    
    @Column(name = "block_high_risk_merchants", nullable = false)
    private boolean blockHighRiskMerchants;
    
    @Column(name = "block_gambling", nullable = false)
    private boolean blockGambling;
    
    @Column(name = "block_adult_content", nullable = false)
    private boolean blockAdultContent;
    
    @Column(name = "block_alcohol", nullable = false)
    private boolean blockAlcohol;
    
    @Column(name = "block_tobacco", nullable = false)
    private boolean blockTobacco;
    
    @Column(name = "block_cryptocurrency", nullable = false)
    private boolean blockCryptocurrency;
    
    @Column(name = "block_cash_advance", nullable = false)
    private boolean blockCashAdvance;
    
    @Column(name = "block_money_transfer", nullable = false)
    private boolean blockMoneyTransfer;
    
    @Column(name = "block_subscription_services", nullable = false)
    private boolean blockSubscriptionServices;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (allowUnknownMerchants == false) {
            allowUnknownMerchants = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if merchant is allowed
     */
    public boolean isMerchantAllowed(String merchantName, String merchantId, String mcc, String category) {
        // Check blocked lists first
        if (blockedMerchants != null && blockedMerchants.contains(merchantName)) {
            return false;
        }
        if (blockedMerchantIds != null && blockedMerchantIds.contains(merchantId)) {
            return false;
        }
        if (blockedMccs != null && blockedMccs.contains(mcc)) {
            return false;
        }
        if (blockedCategories != null && blockedCategories.contains(category)) {
            return false;
        }
        
        // Check category-specific blocks
        if (blockGambling && isGamblingMcc(mcc)) {
            return false;
        }
        if (blockAdultContent && isAdultContentMcc(mcc)) {
            return false;
        }
        if (blockAlcohol && isAlcoholMcc(mcc)) {
            return false;
        }
        if (blockTobacco && isTobaccoMcc(mcc)) {
            return false;
        }
        if (blockCryptocurrency && isCryptocurrencyMcc(mcc)) {
            return false;
        }
        if (blockCashAdvance && isCashAdvanceMcc(mcc)) {
            return false;
        }
        if (blockMoneyTransfer && isMoneyTransferMcc(mcc)) {
            return false;
        }
        
        // If whitelist mode, check allowed lists
        if (whitelistMode) {
            boolean inAllowedList = false;
            
            if (allowedMerchants != null && allowedMerchants.contains(merchantName)) {
                inAllowedList = true;
            }
            if (allowedMerchantIds != null && allowedMerchantIds.contains(merchantId)) {
                inAllowedList = true;
            }
            if (allowedMccs != null && allowedMccs.contains(mcc)) {
                inAllowedList = true;
            }
            if (allowedCategories != null && allowedCategories.contains(category)) {
                inAllowedList = true;
            }
            
            return inAllowedList;
        }
        
        return allowUnknownMerchants;
    }
    
    /**
     * Get spending limit for merchant
     */
    public java.math.BigDecimal getMerchantLimit(String merchantId, String category) {
        if (merchantLimits != null && merchantLimits.containsKey(merchantId)) {
            return merchantLimits.get(merchantId);
        }
        if (categoryLimits != null && categoryLimits.containsKey(category)) {
            return categoryLimits.get(category);
        }
        return null;
    }
    
    private boolean isGamblingMcc(String mcc) {
        return mcc != null && (mcc.equals("7995") || mcc.equals("7801") || mcc.equals("7802"));
    }
    
    private boolean isAdultContentMcc(String mcc) {
        return mcc != null && mcc.equals("5967");
    }
    
    private boolean isAlcoholMcc(String mcc) {
        return mcc != null && (mcc.equals("5921") || mcc.equals("5813"));
    }
    
    private boolean isTobaccoMcc(String mcc) {
        return mcc != null && mcc.equals("5993");
    }
    
    private boolean isCryptocurrencyMcc(String mcc) {
        return mcc != null && mcc.equals("6051");
    }
    
    private boolean isCashAdvanceMcc(String mcc) {
        return mcc != null && mcc.equals("6010");
    }
    
    private boolean isMoneyTransferMcc(String mcc) {
        return mcc != null && (mcc.equals("4829") || mcc.equals("6051"));
    }
}