package com.waqiti.corebanking.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.waqiti.common.security.converters.EncryptedFinancialConverter;
import com.waqiti.common.security.converters.EncryptedPIIConverter;
import com.waqiti.corebanking.exception.BankAccountExceptions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Enterprise-grade BankAccount domain entity for core banking operations.
 *
 * Represents external bank accounts linked to the Waqiti platform for
 * ACH transfers, wire transfers, and other banking integrations.
 *
 * Features:
 * - Field-level encryption for PCI DSS compliance
 * - Comprehensive business rules and validation
 * - Micro-deposit verification workflow
 * - Transaction limit management and monitoring
 * - Integration with Chart of Accounts
 * - Compliance and audit trail support
 *
 * EntityGraph Optimization:
 * - basic: Loads only bank account fields (default)
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Entity
@Table(name = "bank_accounts", indexes = {
    @Index(name = "idx_bank_account_number", columnList = "accountNumber", unique = true),
    @Index(name = "idx_bank_routing_account", columnList = "routingNumber,accountNumber", unique = true),
    @Index(name = "idx_bank_user_id", columnList = "userId"),
    @Index(name = "idx_bank_core_account", columnList = "coreAccountId"),
    @Index(name = "idx_bank_status", columnList = "status"),
    @Index(name = "idx_bank_provider_id", columnList = "providerAccountId"),
    @Index(name = "idx_bank_verification", columnList = "isVerified,verificationAttempts")
})
@EntityListeners(AuditingEntityListener.class)
@NamedEntityGraph(name = "BankAccount.basic")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class BankAccount extends BaseAuditEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Column(name = "core_account_id", nullable = false)
    @NotNull(message = "Core account ID is required")
    private UUID coreAccountId; // Links to Account in core-banking-service
    
    // CRITICAL SECURITY: Encrypt account numbers for PCI DSS compliance
    @Column(name = "account_number", nullable = false, unique = true, columnDefinition = "TEXT")
    @Convert(converter = EncryptedPIIConverter.class)
    @NotBlank(message = "Account number is required")
    @Size(min = 8, max = 20, message = "Account number must be 8-20 characters")
    private String accountNumber;
    
    // CRITICAL SECURITY: Encrypt routing numbers for compliance
    @Column(name = "routing_number", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = EncryptedPIIConverter.class)
    @NotBlank(message = "Routing number is required")
    @Size(min = 9, max = 9, message = "Routing number must be exactly 9 digits")
    private String routingNumber;
    
    // CRITICAL SECURITY: Encrypt account holder name for PII protection
    @Column(name = "account_holder_name", length = 100, columnDefinition = "TEXT")
    @Convert(converter = EncryptedPIIConverter.class)
    @Size(max = 100, message = "Account holder name cannot exceed 100 characters")
    private String accountHolderName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    @NotNull(message = "Account type is required")
    private BankAccountType accountType;
    
    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    private String currency;
    
    // External bank information
    @Column(name = "bank_name", length = 100)
    @Size(max = 100, message = "Bank name cannot exceed 100 characters")
    private String bankName;
    
    @Column(name = "branch_code", length = 20)
    @Size(max = 20, message = "Branch code cannot exceed 20 characters")
    private String branchCode;
    
    @Column(name = "swift_code", length = 11)
    @Size(max = 11, message = "SWIFT code cannot exceed 11 characters")
    private String swiftCode;
    
    @Column(name = "iban", length = 34, columnDefinition = "TEXT")
    @Convert(converter = EncryptedPIIConverter.class)
    @Size(max = 34, message = "IBAN cannot exceed 34 characters")
    private String iban;
    
    // Account status and verification
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull
    @Builder.Default
    private BankAccountStatus status = BankAccountStatus.PENDING_VERIFICATION;
    
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;
    
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;
    
    @Column(name = "verification_date")
    private LocalDateTime verificationDate;
    
    // External provider integration
    @Column(name = "external_bank_id", length = 50)
    @Size(max = 50, message = "External bank ID cannot exceed 50 characters")
    private String externalBankId;
    
    @Column(name = "provider_account_id", length = 100)
    @Size(max = 100, message = "Provider account ID cannot exceed 100 characters")
    private String providerAccountId;
    
    @Column(name = "provider_name", length = 50)
    @Size(max = 50, message = "Provider name cannot exceed 50 characters")
    private String providerName; // Plaid, Yodlee, etc.
    
    // Micro-deposit verification (encrypted for security)
    @Column(name = "micro_deposit_amount_1", precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal microDepositAmount1;
    
    @Column(name = "micro_deposit_amount_2", precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal microDepositAmount2;
    
    @Column(name = "micro_deposit_sent_date")
    private LocalDateTime microDepositSentDate;
    
    @Column(name = "verification_attempts", nullable = false)
    @Builder.Default
    private Integer verificationAttempts = 0;
    
    @Column(name = "max_verification_attempts", nullable = false)
    @Builder.Default
    private Integer maxVerificationAttempts = 3;
    
    @Column(name = "verification_deadline")
    private LocalDateTime verificationDeadline;
    
    // Transaction limits (managed by core banking, mirrored here)
    @Column(name = "daily_limit", precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal dailyLimit;
    
    @Column(name = "monthly_limit", precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    private BigDecimal monthlyLimit;
    
    // Transaction tracking for limits
    @Column(name = "daily_spent", precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    @Builder.Default
    private BigDecimal dailySpent = BigDecimal.ZERO;
    
    @Column(name = "monthly_spent", precision = 19, scale = 4, columnDefinition = "TEXT")
    @Convert(converter = EncryptedFinancialConverter.class)
    @Builder.Default
    private BigDecimal monthlySpent = BigDecimal.ZERO;
    
    @Column(name = "last_transaction_date")
    private LocalDateTime lastTransactionDate;
    
    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Long totalTransactions = 0L;
    
    // Risk and compliance
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Column(name = "compliance_status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplianceStatus complianceStatus = ComplianceStatus.PENDING_REVIEW;
    
    @Column(name = "kyc_status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private KYCStatus kycStatus = KYCStatus.PENDING;
    
    @Column(name = "aml_check_date")
    private LocalDateTime amlCheckDate;
    
    @Column(name = "sanctions_check_date")
    private LocalDateTime sanctionsCheckDate;
    
    // Account closure
    @Column(name = "closed_date")
    private LocalDateTime closedDate;
    
    @Column(name = "closure_reason", length = 500)
    @Size(max = 500, message = "Closure reason cannot exceed 500 characters")
    private String closureReason;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @Transient
    @Builder.Default
    private final Object operationLock = new Object();
    
    /**
     * Creates a new bank account with comprehensive validation
     */
    public static BankAccount create(UUID userId, UUID coreAccountId, String accountNumber, 
            String routingNumber, BankAccountType accountType, String currency, String accountHolderName) {
        
        BankAccount bankAccount = BankAccount.builder()
            .userId(userId)
            .coreAccountId(coreAccountId)
            .accountNumber(validateAndNormalizeAccountNumber(accountNumber))
            .routingNumber(validateAndNormalizeRoutingNumber(routingNumber))
            .accountType(accountType)
            .currency(currency.toUpperCase())
            .accountHolderName(accountHolderName)
            .status(BankAccountStatus.PENDING_VERIFICATION)
            .isVerified(false)
            .isPrimary(false)
            .verificationDeadline(LocalDateTime.now().plusDays(7))
            .verificationAttempts(0)
            .maxVerificationAttempts(3)
            .dailySpent(BigDecimal.ZERO)
            .monthlySpent(BigDecimal.ZERO)
            .totalTransactions(0L)
            .complianceStatus(ComplianceStatus.PENDING_REVIEW)
            .kycStatus(KYCStatus.PENDING)
            .build();
        
        log.info("Created new bank account for user {} linked to core account {} with type {} in currency {}", 
            userId, coreAccountId, accountType, currency);
        
        return bankAccount;
    }
    
    /**
     * Initiates micro-deposit verification process
     */
    public void initiateMicroDepositVerification(BigDecimal amount1, BigDecimal amount2) {
        synchronized (operationLock) {
            validateAccountForVerification();
            validateMicroDepositAmounts(amount1, amount2);
            
            this.microDepositAmount1 = amount1;
            this.microDepositAmount2 = amount2;
            this.microDepositSentDate = LocalDateTime.now();
            this.status = BankAccountStatus.VERIFICATION_IN_PROGRESS;
            this.updatedAt = LocalDateTime.now();
            
            log.info("Initiated micro-deposit verification for bank account {}", this.id);
        }
    }
    
    /**
     * Verifies micro-deposit amounts
     */
    public boolean verifyMicroDeposits(BigDecimal amount1, BigDecimal amount2) {
        synchronized (operationLock) {
            validateVerificationAttempt();
            
            this.verificationAttempts++;
            this.updatedAt = LocalDateTime.now();
            
            boolean isVerified = this.microDepositAmount1.compareTo(amount1) == 0 && 
                                this.microDepositAmount2.compareTo(amount2) == 0;
            
            if (isVerified) {
                this.isVerified = true;
                this.verificationDate = LocalDateTime.now();
                this.status = BankAccountStatus.ACTIVE;
                this.complianceStatus = ComplianceStatus.COMPLIANT;
                
                log.info("Successfully verified bank account {} for user {}", this.id, this.userId);
            } else {
                log.warn("Failed verification attempt {} of {} for bank account {}", 
                    this.verificationAttempts, this.maxVerificationAttempts, this.id);
                
                if (this.verificationAttempts >= this.maxVerificationAttempts) {
                    this.status = BankAccountStatus.VERIFICATION_FAILED;
                    this.complianceStatus = ComplianceStatus.FAILED;
                    log.error("Bank account {} verification failed after {} attempts", 
                        this.id, this.maxVerificationAttempts);
                }
            }
            
            return isVerified;
        }
    }
    
    /**
     * Tracks transaction for limit monitoring
     */
    public void trackTransaction(BigDecimal amount, String transactionType) {
        synchronized (operationLock) {
            if ("withdrawal".equalsIgnoreCase(transactionType) || "transfer".equalsIgnoreCase(transactionType)) {
                this.dailySpent = this.dailySpent.add(amount);
                this.monthlySpent = this.monthlySpent.add(amount);
            }
            
            this.totalTransactions++;
            this.lastTransactionDate = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
            
            log.debug("Tracked {} transaction of {} for bank account {}", 
                transactionType, amount, this.id);
        }
    }
    
    // Business validation methods
    
    private void validateAccountForVerification() {
        if (this.status == BankAccountStatus.CLOSED) {
            throw new InvalidAccountStatusException("Cannot verify a closed account");
        }
    }
    
    private void validateMicroDepositAmounts(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            throw new InvalidVerificationAmountException("Micro-deposit amounts cannot be null");
        }
        
        if (amount1.compareTo(BigDecimal.ZERO) <= 0 || amount2.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidVerificationAmountException("Micro-deposit amounts must be positive");
        }
        
        if (amount1.compareTo(new BigDecimal("1.00")) > 0 || amount2.compareTo(new BigDecimal("1.00")) > 0) {
            throw new InvalidVerificationAmountException("Micro-deposit amounts cannot exceed $1.00");
        }
    }
    
    private void validateVerificationAttempt() {
        if (this.status != BankAccountStatus.VERIFICATION_IN_PROGRESS) {
            throw new InvalidAccountStatusException("Account is not in verification progress status");
        }
        
        if (this.verificationAttempts >= this.maxVerificationAttempts) {
            throw new MaxVerificationAttemptsExceededException("Maximum verification attempts exceeded");
        }
        
        if (LocalDateTime.now().isAfter(this.verificationDeadline)) {
            throw new VerificationExpiredException("Verification deadline has passed");
        }
    }
    
    private static String validateAndNormalizeAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new InvalidAccountNumberException("Account number cannot be null or empty");
        }
        
        String normalized = accountNumber.trim().replaceAll("[^0-9]", "");
        
        if (normalized.length() < 8 || normalized.length() > 20) {
            throw new InvalidAccountNumberException("Account number must be 8-20 digits");
        }
        
        return normalized;
    }
    
    private static String validateAndNormalizeRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.trim().isEmpty()) {
            throw new InvalidRoutingNumberException("Routing number cannot be null or empty");
        }
        
        String normalized = routingNumber.trim().replaceAll("[^0-9]", "");
        
        if (normalized.length() != 9) {
            throw new InvalidRoutingNumberException("Routing number must be exactly 9 digits");
        }
        
        if (!isValidRoutingNumberChecksum(normalized)) {
            throw new InvalidRoutingNumberException("Invalid routing number checksum");
        }
        
        return normalized;
    }
    
    private static boolean isValidRoutingNumberChecksum(String routingNumber) {
        if (routingNumber.length() != 9) return false;
        
        try {
            int checksum = 0;
            for (int i = 0; i < 8; i++) {
                int digit = Character.getNumericValue(routingNumber.charAt(i));
                int weight = (i % 3 == 0) ? 3 : (i % 3 == 1) ? 7 : 1;
                checksum += digit * weight;
            }
            
            int expectedCheck = (10 - (checksum % 10)) % 10;
            int actualCheck = Character.getNumericValue(routingNumber.charAt(8));
            
            return expectedCheck == actualCheck;
        } catch (Exception e) {
            return false;
        }
    }
    
    // Business query methods
    
    public boolean canProcessTransaction(BigDecimal amount) {
        return this.status == BankAccountStatus.ACTIVE && 
               this.isVerified &&
               this.complianceStatus == ComplianceStatus.COMPLIANT;
    }
    
    public boolean isExpired() {
        return this.status == BankAccountStatus.CLOSED || 
               (this.verificationDeadline != null && LocalDateTime.now().isAfter(this.verificationDeadline)) ||
               (this.verificationAttempts != null && this.maxVerificationAttempts != null && 
                this.verificationAttempts >= this.maxVerificationAttempts);
    }
    
    public boolean requiresVerification() {
        return !this.isVerified && this.status != BankAccountStatus.CLOSED && !isExpired();
    }
    
    // Enums
    
    public enum BankAccountType {
        CHECKING("Checking Account"),
        SAVINGS("Savings Account"),
        MONEY_MARKET("Money Market Account"),
        BUSINESS_CHECKING("Business Checking"),
        BUSINESS_SAVINGS("Business Savings"),
        CD("Certificate of Deposit"),
        IRA("Individual Retirement Account");
        
        private final String displayName;
        
        BankAccountType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum BankAccountStatus {
        PENDING_VERIFICATION("Pending Verification"),
        VERIFICATION_IN_PROGRESS("Verification in Progress"),
        ACTIVE("Active"),
        SUSPENDED("Suspended"),
        CLOSED("Closed"),
        VERIFICATION_FAILED("Verification Failed"),
        FROZEN("Frozen");
        
        private final String displayName;
        
        BankAccountStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public boolean isActive() {
            return this == ACTIVE;
        }
        
        public boolean canTransact() {
            return this == ACTIVE;
        }
    }
    
    public enum ComplianceStatus {
        PENDING_REVIEW("Pending Review"),
        COMPLIANT("Compliant"),
        NON_COMPLIANT("Non-Compliant"),
        FAILED("Failed"),
        SUSPENDED("Suspended"),
        REQUIRES_MANUAL_REVIEW("Requires Manual Review");
        
        private final String displayName;
        
        ComplianceStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum KYCStatus {
        PENDING("Pending"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        EXPIRED("Expired"),
        REQUIRES_UPDATE("Requires Update");
        
        private final String displayName;
        
        KYCStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}