package com.waqiti.account.entity;

import com.waqiti.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Formula;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Account entity representing a financial account in the system
 * 
 * This entity extends BaseEntity to inherit common fields and audit support,
 * while adding account-specific fields for comprehensive financial management.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_account_user_id", columnList = "user_id"),
    @Index(name = "idx_account_number", columnList = "account_number", unique = true),
    @Index(name = "idx_account_type", columnList = "account_type"),
    @Index(name = "idx_account_status", columnList = "status"),
    @Index(name = "idx_account_created_at", columnList = "created_at"),
    @Index(name = "idx_account_parent_id", columnList = "parent_account_id")
})
@Getter
@Setter
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Audited
@EntityListeners({AccountEntityListener.class})
public class Account extends BaseEntity {
    
    // Thread-safe SecureRandom for secure account number generation
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * Unique account number
     */
    @Column(name = "account_number", unique = true, nullable = false, length = 20)
    @ToString.Include
    private String accountNumber;
    
    /**
     * User who owns this account
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    /**
     * Account type enumeration
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;
    
    /**
     * Account status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;
    
    /**
     * Account name/alias
     */
    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;
    
    /**
     * Account currency ISO code
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    /**
     * Current account balance
     */
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;
    
    /**
     * Available balance (considering holds)
     */
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;
    
    /**
     * Ledger balance (end of day balance)
     */
    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal ledgerBalance;
    
    /**
     * Total amount on hold
     */
    @Formula("(balance - available_balance)")
    private BigDecimal holdAmount;
    
    /**
     * Account category
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_category", length = 20)
    private AccountCategory accountCategory;
    
    /**
     * Parent account for sub-accounts
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    private Account parentAccount;
    
    /**
     * Child sub-accounts
     */
    @OneToMany(mappedBy = "parentAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Account> subAccounts = new HashSet<>();
    
    /**
     * Overdraft protection enabled
     */
    @Column(name = "overdraft_protection", nullable = false)
    private Boolean overdraftProtection;
    
    /**
     * Overdraft limit
     */
    @Column(name = "overdraft_limit", precision = 19, scale = 4)
    private BigDecimal overdraftLimit;
    
    /**
     * Daily transaction limit
     */
    @Column(name = "daily_transaction_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;
    
    /**
     * Monthly transaction limit
     */
    @Column(name = "monthly_transaction_limit", precision = 19, scale = 4)
    private BigDecimal monthlyTransactionLimit;
    
    /**
     * Daily spent amount (reset daily)
     */
    @Column(name = "daily_spent", precision = 19, scale = 4)
    private BigDecimal dailySpent;
    
    /**
     * Monthly spent amount (reset monthly)
     */
    @Column(name = "monthly_spent", precision = 19, scale = 4)  
    private BigDecimal monthlySpent;
    
    /**
     * Account tier level
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier_level", length = 20)
    private TierLevel tierLevel;
    
    /**
     * KYC verification level
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", length = 20)
    private KycLevel kycLevel;
    
    /**
     * KYC verified date
     */
    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;
    
    /**
     * International transactions enabled
     */
    @Column(name = "international_enabled", nullable = false)
    private Boolean internationalEnabled;
    
    /**
     * Virtual card enabled
     */
    @Column(name = "virtual_card_enabled", nullable = false)
    private Boolean virtualCardEnabled;
    
    /**
     * Account opening date
     */
    @Column(name = "opened_at")
    private LocalDateTime openedAt;
    
    /**
     * Account closure date
     */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    
    /**
     * Last transaction date
     */
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;
    
    /**
     * Interest rate (for savings accounts)
     */
    @Column(name = "interest_rate", precision = 5, scale = 4)
    private BigDecimal interestRate;
    
    /**
     * Last interest calculation date
     */
    @Column(name = "last_interest_calculated_at")
    private LocalDateTime lastInterestCalculatedAt;
    
    /**
     * Account frozen flag
     */
    @Column(name = "frozen", nullable = false)
    private Boolean frozen;
    
    /**
     * Freeze reason
     */
    @Column(name = "freeze_reason", length = 500)
    private String freezeReason;
    
    /**
     * Frozen date
     */
    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;
    
    /**
     * Notification preferences (JSON)
     */
    @Column(name = "notification_preferences", columnDefinition = "jsonb")
    private String notificationPreferences;
    
    /**
     * Risk score
     */
    @Column(name = "risk_score")
    private Integer riskScore;
    
    /**
     * Compliance flags (JSON)
     */
    @Column(name = "compliance_flags", columnDefinition = "jsonb")
    private String complianceFlags;
    
    /**
     * Account tags for categorization
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "account_tags", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "tag")
    private Set<String> tags = new HashSet<>();
    
    /**
     * Associated payment methods
     */
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<PaymentMethod> paymentMethods = new HashSet<>();
    
    /**
     * Account transactions
     */
    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    @OrderBy("transactionDate DESC")
    private Set<Transaction> transactions = new HashSet<>();
    
    /**
     * Pre-persist initialization
     */
    @Override
    protected void onPrePersist() {
        super.onPrePersist();
        
        if (accountNumber == null) {
            accountNumber = generateAccountNumber();
        }
        
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        
        if (availableBalance == null) {
            availableBalance = balance;
        }
        
        if (ledgerBalance == null) {
            ledgerBalance = balance;
        }
        
        if (dailySpent == null) {
            dailySpent = BigDecimal.ZERO;
        }
        
        if (monthlySpent == null) {
            monthlySpent = BigDecimal.ZERO;
        }
        
        if (status == null) {
            status = AccountStatus.PENDING_ACTIVATION;
        }
        
        if (overdraftProtection == null) {
            overdraftProtection = false;
        }
        
        if (internationalEnabled == null) {
            internationalEnabled = false;
        }
        
        if (virtualCardEnabled == null) {
            virtualCardEnabled = false;
        }
        
        if (frozen == null) {
            frozen = false;
        }
        
        if (openedAt == null) {
            openedAt = LocalDateTime.now();
        }
        
        if (accountCategory == null) {
            accountCategory = AccountCategory.PERSONAL;
        }
        
        if (tierLevel == null) {
            tierLevel = TierLevel.STANDARD;
        }
        
        if (kycLevel == null) {
            kycLevel = KycLevel.LEVEL_1;
        }
        
        // Set business key
        setBusinessKey(accountNumber);
    }
    
    /**
     * Generate unique account number using cryptographically secure random generation.
     * Format: WAQT-YYYYMMDD-XXXXXX where XXXXXX is a secure 6-digit number.
     */
    private String generateAccountNumber() {
        LocalDateTime now = LocalDateTime.now();
        String datePart = String.format("%04d%02d%02d", 
            now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        
        // Generate cryptographically secure 6-digit random number
        String randomPart = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        
        return String.format("WAQT-%s-%s", datePart, randomPart);
    }
    
    /**
     * Check if account is active
     */
    public boolean isAccountActive() {
        return status == AccountStatus.ACTIVE && 
               !frozen && 
               getActive() && 
               !getDeleted();
    }
    
    /**
     * Check if transaction is allowed
     */
    public boolean canTransact(BigDecimal amount) {
        if (!isAccountActive()) {
            return false;
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // Check available balance
        BigDecimal totalAvailable = availableBalance;
        if (overdraftProtection && overdraftLimit != null) {
            totalAvailable = totalAvailable.add(overdraftLimit);
        }
        
        return amount.compareTo(totalAvailable) <= 0;
    }
    
    /**
     * Check daily limit
     */
    public boolean isWithinDailyLimit(BigDecimal amount) {
        if (dailyTransactionLimit == null) {
            return true;
        }
        
        BigDecimal totalDaily = dailySpent.add(amount);
        return totalDaily.compareTo(dailyTransactionLimit) <= 0;
    }
    
    /**
     * Check monthly limit
     */
    public boolean isWithinMonthlyLimit(BigDecimal amount) {
        if (monthlyTransactionLimit == null) {
            return true;
        }
        
        BigDecimal totalMonthly = monthlySpent.add(amount);
        return totalMonthly.compareTo(monthlyTransactionLimit) <= 0;
    }
    
    /**
     * Update balance
     */
    public void updateBalance(BigDecimal amount, TransactionType type) {
        if (type == TransactionType.DEBIT) {
            balance = balance.subtract(amount);
            availableBalance = availableBalance.subtract(amount);
        } else {
            balance = balance.add(amount);
            availableBalance = availableBalance.add(amount);
        }
        
        lastTransactionAt = LocalDateTime.now();
    }
    
    /**
     * Place hold on funds
     */
    public void placeHold(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            availableBalance = availableBalance.subtract(amount);
        }
    }
    
    /**
     * Release hold on funds
     */
    public void releaseHold(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            availableBalance = availableBalance.add(amount);
        }
    }
    
    /**
     * Freeze account
     */
    public void freeze(String reason) {
        this.frozen = true;
        this.freezeReason = reason;
        this.frozenAt = LocalDateTime.now();
        this.status = AccountStatus.FROZEN;
    }
    
    /**
     * Unfreeze account
     */
    public void unfreeze() {
        this.frozen = false;
        this.freezeReason = null;
        this.frozenAt = null;
        this.status = AccountStatus.ACTIVE;
    }
    
    /**
     * Close account
     */
    public void close() {
        this.status = AccountStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.setActive(false);
    }
    
    /**
     * Account type enumeration
     */
    public enum AccountType {
        SAVINGS,
        CHECKING,
        INVESTMENT,
        CREDIT,
        LOAN,
        WALLET
    }
    
    /**
     * Account status enumeration
     */
    public enum AccountStatus {
        PENDING_ACTIVATION,
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        FROZEN,
        CLOSED
    }
    
    /**
     * Account category enumeration
     */
    public enum AccountCategory {
        PERSONAL,
        BUSINESS,
        JOINT,
        TRUST,
        CORPORATE
    }
    
    /**
     * Tier level enumeration
     */
    public enum TierLevel {
        BASIC,
        STANDARD,
        PREMIUM,
        VIP,
        PLATINUM
    }
    
    /**
     * KYC level enumeration
     */
    public enum KycLevel {
        LEVEL_0,
        LEVEL_1,
        LEVEL_2,
        LEVEL_3
    }
    
    /**
     * Transaction type enumeration
     */
    public enum TransactionType {
        CREDIT,
        DEBIT
    }
}

