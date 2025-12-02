package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Card Control Entity
 * Manages transaction controls and restrictions for virtual cards
 */
@Entity
@Table(name = "card_controls", indexes = {
    @Index(name = "idx_card_id", columnList = "card_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CardControl {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(name = "card_id", nullable = false, unique = true)
    private String cardId;
    
    @Column(name = "allow_online_transactions", nullable = false)
    private boolean allowOnlineTransactions;
    
    @Column(name = "allow_international_transactions", nullable = false)
    private boolean allowInternationalTransactions;
    
    @Column(name = "allow_atm_withdrawals", nullable = false)
    private boolean allowAtmWithdrawals;
    
    @Column(name = "allow_contactless_payments", nullable = false)
    private boolean allowContactlessPayments;
    
    @Column(name = "allow_recurring_payments", nullable = false)
    private boolean allowRecurringPayments;
    
    @Column(name = "allow_chip_transactions", nullable = false)
    private boolean allowChipTransactions;
    
    @Column(name = "allow_magnetic_stripe", nullable = false)
    private boolean allowMagneticStripe;
    
    @Column(name = "allow_manual_key_entry", nullable = false)
    private boolean allowManualKeyEntry;
    
    @Column(name = "max_transactions")
    private Integer maxTransactions;
    
    @Column(name = "max_daily_transactions")
    private Integer maxDailyTransactions;
    
    @Column(name = "max_weekly_transactions")
    private Integer maxWeeklyTransactions;
    
    @Column(name = "max_monthly_transactions")
    private Integer maxMonthlyTransactions;
    
    @ElementCollection
    @CollectionTable(
        name = "card_allowed_countries",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @Column(name = "country_code")
    private List<String> allowedCountries;
    
    @ElementCollection
    @CollectionTable(
        name = "card_blocked_countries",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @Column(name = "country_code")
    private List<String> blockedCountries;
    
    @ElementCollection
    @CollectionTable(
        name = "card_allowed_merchant_categories",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @Column(name = "mcc")
    private List<String> allowedMerchantCategories;
    
    @ElementCollection
    @CollectionTable(
        name = "card_blocked_merchant_categories",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @Column(name = "mcc")
    private List<String> blockedMerchantCategories;
    
    @ElementCollection
    @CollectionTable(
        name = "card_allowed_merchants",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @Column(name = "merchant_id")
    private List<String> allowedMerchants;
    
    @ElementCollection
    @CollectionTable(
        name = "card_blocked_merchants",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @Column(name = "merchant_id")
    private List<String> blockedMerchants;
    
    @ElementCollection
    @CollectionTable(
        name = "card_time_restrictions",
        joinColumns = @JoinColumn(name = "card_control_id")
    )
    @MapKeyColumn(name = "day_of_week")
    @Column(name = "time_ranges")
    private Map<String, String> timeRestrictions;
    
    @Column(name = "daily_start_time")
    private LocalTime dailyStartTime;
    
    @Column(name = "daily_end_time")
    private LocalTime dailyEndTime;
    
    @Column(name = "expiry_minutes")
    private Integer expiryMinutes;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "velocity_check_enabled", nullable = false)
    private boolean velocityCheckEnabled;
    
    @Column(name = "fraud_check_enabled", nullable = false)
    private boolean fraudCheckEnabled;
    
    @Column(name = "high_risk_merchant_block", nullable = false)
    private boolean highRiskMerchantBlock;
    
    @Column(name = "gambling_block", nullable = false)
    private boolean gamblingBlock;
    
    @Column(name = "adult_content_block", nullable = false)
    private boolean adultContentBlock;
    
    @Column(name = "crypto_purchase_block", nullable = false)
    private boolean cryptoPurchaseBlock;
    
    @Column(name = "cash_advance_block", nullable = false)
    private boolean cashAdvanceBlock;
    
    @Column(name = "require_mfa_for_online", nullable = false)
    private boolean requireMfaForOnline;
    
    @Column(name = "require_mfa_for_international", nullable = false)
    private boolean requireMfaForInternational;
    
    @Column(name = "require_mfa_above_amount")
    private java.math.BigDecimal requireMfaAboveAmount;
    
    @Column(name = "notification_on_all_transactions", nullable = false)
    private boolean notificationOnAllTransactions;
    
    @Column(name = "notification_on_declined", nullable = false)
    private boolean notificationOnDeclined;
    
    @Column(name = "notification_on_high_amount", nullable = false)
    private boolean notificationOnHighAmount;
    
    @Column(name = "high_amount_threshold")
    private java.math.BigDecimal highAmountThreshold;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (allowOnlineTransactions == false) {
            allowOnlineTransactions = true;
        }
        if (allowInternationalTransactions == false) {
            allowInternationalTransactions = true;
        }
        if (allowAtmWithdrawals == false) {
            allowAtmWithdrawals = true;
        }
        if (allowContactlessPayments == false) {
            allowContactlessPayments = true;
        }
        if (allowRecurringPayments == false) {
            allowRecurringPayments = true;
        }
        if (allowChipTransactions == false) {
            allowChipTransactions = true;
        }
        if (allowMagneticStripe == false) {
            allowMagneticStripe = true;
        }
        if (allowManualKeyEntry == false) {
            allowManualKeyEntry = true;
        }
        if (velocityCheckEnabled == false) {
            velocityCheckEnabled = true;
        }
        if (fraudCheckEnabled == false) {
            fraudCheckEnabled = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if card control allows a transaction
     */
    public boolean allowsTransaction(String transactionType, String countryCode, String merchantCategory) {
        // Check transaction type
        if ("ONLINE".equals(transactionType) && !allowOnlineTransactions) {
            return false;
        }
        if ("INTERNATIONAL".equals(transactionType) && !allowInternationalTransactions) {
            return false;
        }
        if ("ATM".equals(transactionType) && !allowAtmWithdrawals) {
            return false;
        }
        if ("CONTACTLESS".equals(transactionType) && !allowContactlessPayments) {
            return false;
        }
        
        // Check country restrictions
        if (blockedCountries != null && blockedCountries.contains(countryCode)) {
            return false;
        }
        if (allowedCountries != null && !allowedCountries.isEmpty() && !allowedCountries.contains(countryCode)) {
            return false;
        }
        
        // Check merchant category restrictions
        if (blockedMerchantCategories != null && blockedMerchantCategories.contains(merchantCategory)) {
            return false;
        }
        if (allowedMerchantCategories != null && !allowedMerchantCategories.isEmpty() && 
            !allowedMerchantCategories.contains(merchantCategory)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if card has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if transaction is within time restrictions
     */
    public boolean isWithinTimeRestrictions() {
        if (dailyStartTime == null || dailyEndTime == null) {
            return true;
        }
        
        LocalTime now = LocalTime.now();
        if (dailyStartTime.isBefore(dailyEndTime)) {
            return !now.isBefore(dailyStartTime) && !now.isAfter(dailyEndTime);
        } else {
            // Crosses midnight
            return !now.isBefore(dailyStartTime) || !now.isAfter(dailyEndTime);
        }
    }
}