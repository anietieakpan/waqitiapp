package com.waqiti.business.domain;

import com.waqiti.common.encryption.EncryptedStringConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "business_accounts", indexes = {
        @Index(name = "idx_business_accounts_owner", columnList = "owner_id"),
        @Index(name = "idx_business_accounts_status", columnList = "status"),
        @Index(name = "idx_business_accounts_tier", columnList = "tier"),
        @Index(name = "idx_business_accounts_type", columnList = "business_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccount {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    
    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;
    
    @Column(name = "business_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private BusinessType businessType;
    
    @Column(name = "account_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;
    
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status;
    
    @Column(name = "tier", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Tier tier;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "timezone", length = 50)
    private String timezone = "UTC";
    
    @Column(name = "fiscal_year_start")
    @Enumerated(EnumType.STRING)
    private Month fiscalYearStart = Month.JANUARY;
    
    @Column(name = "balance", precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "available_balance", precision = 19, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;
    
    @Column(name = "pending_balance", precision = 19, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;
    
    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;
    
    @Column(name = "monthly_spending", precision = 19, scale = 2)
    private BigDecimal monthlySpending = BigDecimal.ZERO;
    
    @Column(name = "monthly_income", precision = 19, scale = 2)
    private BigDecimal monthlyIncome = BigDecimal.ZERO;
    
    // Business verification status
    @Column(name = "verification_status", length = 20)
    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
    
    @Column(name = "verification_completed_at")
    private LocalDateTime verificationCompletedAt;
    
    // Tax and compliance
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 500)
    private String taxId; // PCI DSS: Encrypted EIN/Tax ID
    
    @Column(name = "sales_tax_enabled")
    private Boolean salesTaxEnabled = false;
    
    @Column(name = "tax_exempt")
    private Boolean taxExempt = false;
    
    @Column(name = "tax_exempt_number", length = 50)
    private String taxExemptNumber;
    
    // Account configuration
    @Type(type = "jsonb")
    @Column(name = "settings", columnDefinition = "jsonb")
    private Map<String, Object> settings;
    
    @Type(type = "jsonb")
    @Column(name = "features", columnDefinition = "jsonb")
    private Map<String, Object> features;
    
    @Type(type = "jsonb")
    @Column(name = "limits", columnDefinition = "jsonb")
    private Map<String, Object> limits;
    
    @Type(type = "jsonb")
    @Column(name = "payment_methods", columnDefinition = "jsonb")
    private Map<String, Object> paymentMethods;
    
    @Type(type = "jsonb")
    @Column(name = "integration_settings", columnDefinition = "jsonb")
    private Map<String, Object> integrationSettings;
    
    // Subscription and billing
    @Column(name = "subscription_id", length = 100)
    private String subscriptionId;
    
    @Column(name = "subscription_status", length = 20)
    private String subscriptionStatus;
    
    @Column(name = "billing_cycle", length = 20)
    private String billingCycle = "MONTHLY";
    
    @Column(name = "next_billing_date")
    private LocalDateTime nextBillingDate;
    
    @Column(name = "monthly_fee", precision = 19, scale = 2)
    private BigDecimal monthlyFee = BigDecimal.ZERO;
    
    // Analytics and metrics
    @Column(name = "total_transactions")
    private Long totalTransactions = 0L;
    
    @Column(name = "total_volume", precision = 19, scale = 2)
    private BigDecimal totalVolume = BigDecimal.ZERO;
    
    @Column(name = "average_transaction_amount", precision = 19, scale = 2)
    private BigDecimal averageTransactionAmount = BigDecimal.ZERO;
    
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;
    
    @Column(name = "team_member_count")
    private Integer teamMemberCount = 0;
    
    @Column(name = "active_invoice_count")
    private Integer activeInvoiceCount = 0;
    
    @Column(name = "pending_expense_count")
    private Integer pendingExpenseCount = 0;
    
    // Security and compliance
    @Column(name = "kyb_status", length = 20)
    @Enumerated(EnumType.STRING)
    private KYBStatus kybStatus = KYBStatus.PENDING;
    
    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel = RiskLevel.LOW;
    
    @Column(name = "compliance_status", length = 20)
    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus = ComplianceStatus.PENDING;
    
    @Column(name = "last_compliance_review")
    private LocalDateTime lastComplianceReview;
    
    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled = false;
    
    @Column(name = "ip_whitelist_enabled")
    private Boolean ipWhitelistEnabled = false;
    
    @Type(type = "jsonb")
    @Column(name = "security_settings", columnDefinition = "jsonb")
    private Map<String, Object> securitySettings;
    
    // API and integrations
    @Column(name = "api_key_id", length = 100)
    private String apiKeyId;
    
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;
    
    @Column(name = "webhook_secret", length = 100)
    private String webhookSecret;
    
    @Type(type = "jsonb")
    @Column(name = "api_permissions", columnDefinition = "jsonb")
    private Map<String, Object> apiPermissions;
    
    // Metadata and tracking
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
    
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;
    
    @Column(name = "upgraded_at")
    private LocalDateTime upgradedAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (status == Status.ACTIVE && activatedAt == null) {
            activatedAt = LocalDateTime.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        if (status == Status.ACTIVE && activatedAt == null) {
            activatedAt = LocalDateTime.now();
        } else if (status == Status.SUSPENDED && suspendedAt == null) {
            suspendedAt = LocalDateTime.now();
        }
    }
    
    // Business logic methods
    public boolean isActive() {
        return status == Status.ACTIVE;
    }
    
    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }
    
    public boolean isSuspended() {
        return status == Status.SUSPENDED;
    }
    
    public boolean hasFeature(String featureName) {
        return features != null && 
               Boolean.TRUE.equals(features.get(featureName));
    }
    
    public Object getLimit(String limitName) {
        return limits != null ? limits.get(limitName) : null;
    }
    
    public boolean isWithinLimit(String limitName, Number value) {
        Object limitObj = getLimit(limitName);
        if (limitObj == null) return true; // No limit set
        
        if (limitObj instanceof Number) {
            Number limit = (Number) limitObj;
            if (limit.intValue() == -1) return true; // Unlimited
            return value.doubleValue() <= limit.doubleValue();
        }
        
        return true;
    }
    
    public boolean canProcessTransaction(BigDecimal amount) {
        if (!isActive() || !isVerified()) return false;

        // Check transaction amount limit
        Object maxTransactionLimit = getLimit("maxTransactionAmount");
        if (maxTransactionLimit instanceof Number) {
            Number limit = (Number) maxTransactionLimit;
            if (limit.intValue() != -1) {
                BigDecimal limitBD = convertNumberToBigDecimal(limit);
                if (amount.compareTo(limitBD) > 0) {
                    return false;
                }
            }
        }

        // Check monthly spending limit
        Object monthlyLimit = getLimit("monthlyTransactionLimit");
        if (monthlyLimit instanceof Number) {
            Number limit = (Number) monthlyLimit;
            if (limit.intValue() != -1) {
                BigDecimal limitBD = convertNumberToBigDecimal(limit);
                if (monthlySpending.add(amount).compareTo(limitBD) > 0) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Safely converts a Number to BigDecimal without precision loss.
     *
     * This method avoids the dangerous pattern of Number.doubleValue() which can
     * introduce floating-point precision errors for large financial amounts.
     *
     * @param number The Number to convert
     * @return BigDecimal representation with full precision
     */
    private BigDecimal convertNumberToBigDecimal(Number number) {
        if (number == null) {
            return BigDecimal.ZERO;
        }

        // Handle BigDecimal directly
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }

        // Handle Long and Integer without precision loss
        if (number instanceof Long || number instanceof Integer) {
            return BigDecimal.valueOf(number.longValue());
        }

        // For other Number types, use String conversion to preserve precision
        // This handles Double, Float, BigInteger, AtomicInteger, etc.
        return new BigDecimal(number.toString());
    }
    
    public boolean hasTeamCapacity() {
        Object maxTeamMembers = getLimit("maxTeamMembers");
        if (maxTeamMembers instanceof Number) {
            Number limit = (Number) maxTeamMembers;
            if (limit.intValue() == -1) return true; // Unlimited
            return teamMemberCount < limit.intValue();
        }
        return true;
    }
    
    public boolean isPremiumTier() {
        return tier.ordinal() >= Tier.PROFESSIONAL.ordinal();
    }
    
    public boolean isEnterpriseTier() {
        return tier == Tier.ENTERPRISE;
    }
    
    public String getDisplayName() {
        return businessName;
    }
    
    public String getTierDisplayName() {
        switch (tier) {
            case STARTER: return "Starter";
            case PROFESSIONAL: return "Professional";
            case ENTERPRISE: return "Enterprise";
            default: return tier.name();
        }
    }
    
    public BigDecimal getEffectiveBalance() {
        return availableBalance != null ? availableBalance : balance;
    }
    
    public boolean requiresCompliance() {
        return totalVolume.compareTo(BigDecimal.valueOf(100000)) > 0 || // $100k+ volume
               isPremiumTier() ||
               businessType == BusinessType.FINANCIAL_SERVICES ||
               businessType == BusinessType.HEALTHCARE;
    }
    
    // Enums
    public enum BusinessType {
        SOLE_PROPRIETORSHIP,
        PARTNERSHIP,
        LLC,
        CORPORATION,
        S_CORPORATION,
        NON_PROFIT,
        GOVERNMENT,
        FINANCIAL_SERVICES,
        HEALTHCARE,
        TECHNOLOGY,
        RETAIL,
        MANUFACTURING,
        CONSULTING,
        REAL_ESTATE,
        HOSPITALITY,
        EDUCATION,
        OTHER
    }
    
    public enum AccountType {
        STANDARD,
        PREMIUM,
        ENTERPRISE,
        NON_PROFIT,
        GOVERNMENT
    }
    
    public enum Status {
        PENDING,
        ACTIVE,
        SUSPENDED,
        CLOSED,
        UNDER_REVIEW
    }
    
    public enum Tier {
        STARTER,
        PROFESSIONAL,
        ENTERPRISE
    }
    
    public enum VerificationStatus {
        PENDING,
        IN_PROGRESS,
        VERIFIED,
        REJECTED,
        REQUIRES_ADDITIONAL_INFO
    }
    
    public enum KYBStatus {
        PENDING,
        IN_PROGRESS,
        VERIFIED,
        REJECTED,
        EXPIRED
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }
    
    public enum ComplianceStatus {
        PENDING,
        COMPLIANT,
        NON_COMPLIANT,
        UNDER_REVIEW,
        EXEMPTED
    }
}