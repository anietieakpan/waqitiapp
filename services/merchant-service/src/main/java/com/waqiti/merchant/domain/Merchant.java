package com.waqiti.merchant.domain;

import com.waqiti.common.encryption.EncryptedStringConverter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "merchants", indexes = {
    @Index(name = "idx_merchants_user", columnList = "user_id"),
    @Index(name = "idx_merchants_status", columnList = "status"),
    @Index(name = "idx_merchants_tier", columnList = "tier"),
    @Index(name = "idx_merchants_industry", columnList = "industry")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Merchant {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "merchant_id", unique = true, nullable = false, length = 50)
    private String merchantId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false)
    private BusinessType businessType;
    
    @Column(name = "business_description", columnDefinition = "TEXT")
    private String businessDescription;
    
    @Column(name = "business_registration_number", length = 100)
    private String businessRegistrationNumber;
    
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_identifier", length = 500)
    private String taxIdentifier; // PCI DSS: Encrypted EIN/Tax ID
    
    @Column(name = "website")
    private String website;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "industry", nullable = false)
    private Industry industry;
    
    @Embedded
    private BusinessAddress businessAddress;
    
    @Embedded
    private ContactDetails contactDetails;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MerchantStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    @Builder.Default
    private MerchantTier tier = MerchantTier.STANDARD;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;
    
    @Column(name = "risk_score")
    private Double riskScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_stage")
    private OnboardingStage onboardingStage;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "kyb_status")
    private KYBStatus kybStatus;
    
    @Column(name = "kyb_completed_at")
    private Instant kybCompletedAt;
    
    @Column(name = "kyb_provider")
    private String kybProvider;
    
    @Column(name = "kyb_reference_id")
    private String kybReferenceId;
    
    @Column(name = "rejection_reason")
    private String rejectionReason;
    
    @Embedded
    private ProcessingSettings processingSettings;
    
    @Embedded
    private ComplianceSettings complianceSettings;
    
    @Embedded
    private ApiSettings apiSettings;
    
    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Store> stores;
    
    @ElementCollection
    @CollectionTable(name = "merchant_metadata", joinColumns = @JoinColumn(name = "merchant_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @Column(name = "logo_url")
    private String logoUrl;
    
    @Column(name = "banner_url")
    private String bannerUrl;
    
    @Column(name = "qr_code_url")
    private String qrCodeUrl;
    
    @Column(name = "first_transaction_at")
    private Instant firstTransactionAt;
    
    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;
    
    @Column(name = "total_transactions")
    @Builder.Default
    private Long totalTransactions = 0L;
    
    @Column(name = "total_volume", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalVolume = BigDecimal.ZERO;
    
    @Column(name = "total_stores")
    @Builder.Default
    private Integer totalStores = 0;
    
    @Column(name = "last_settlement_at")
    private Instant lastSettlementAt;
    
    @Column(name = "last_risk_assessment")
    private Instant lastRiskAssessment;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (merchantId == null) {
            merchantId = "MERCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
    
    /**
     * Check if merchant is active and can process transactions.
     */
    public boolean isActive() {
        return status == MerchantStatus.ACTIVE && 
               kybStatus == KYBStatus.APPROVED;
    }
    
    /**
     * Update transaction statistics.
     */
    public void updateTransactionStats(BigDecimal amount) {
        this.totalTransactions++;
        this.totalVolume = this.totalVolume.add(amount);
        this.lastTransactionAt = Instant.now();
        
        if (this.firstTransactionAt == null) {
            this.firstTransactionAt = Instant.now();
        }
    }
}

/**
 * Business address information.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class BusinessAddress {
    
    @Column(name = "street_address")
    private String streetAddress;
    
    @Column(name = "city")
    private String city;
    
    @Column(name = "state_province")
    private String stateProvince;
    
    @Column(name = "postal_code")
    private String postalCode;
    
    @Column(name = "country")
    private String country;
    
    @Column(name = "latitude")
    private Double latitude;
    
    @Column(name = "longitude")
    private Double longitude;
}

/**
 * Contact details for merchant.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ContactDetails {
    
    @Column(name = "contact_email")
    private String email;
    
    @Column(name = "contact_phone")
    private String phoneNumber;
    
    @Column(name = "contact_person_name")
    private String contactPersonName;
    
    @Column(name = "contact_person_title")
    private String contactPersonTitle;
    
    @Column(name = "support_email")
    private String supportEmail;
    
    @Column(name = "support_phone")
    private String supportPhone;
}

/**
 * Processing settings and fee structure.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProcessingSettings {
    
    @Embedded
    private FeeStructure feeStructure;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_schedule")
    private SettlementSchedule settlementSchedule;
    
    @Column(name = "hold_reserve", precision = 5, scale = 4)
    private BigDecimal holdReserve;
    
    @Embedded
    private TransactionLimits transactionLimits;
    
    @Column(name = "settlement_account")
    private String settlementAccount;
    
    @Column(name = "last_fee_optimization")
    private Instant lastFeeOptimization;
}

/**
 * Fee structure configuration.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class FeeStructure {
    
    @Column(name = "processing_fee_rate", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal processingFeeRate = new BigDecimal("0.029");
    
    @Column(name = "fixed_fee", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal fixedFee = new BigDecimal("0.30");
    
    @Column(name = "monthly_fee", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal monthlyFee = BigDecimal.ZERO;
    
    @Column(name = "chargeback_fee", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal chargebackFee = new BigDecimal("15.00");
}

/**
 * Transaction limits configuration.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TransactionLimits {
    
    @Column(name = "daily_transaction_limit", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal dailyLimit = new BigDecimal("50000");
    
    @Column(name = "monthly_transaction_limit", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal monthlyLimit = new BigDecimal("1000000");
    
    @Column(name = "single_transaction_limit", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal singleTransactionLimit = new BigDecimal("10000");
}

/**
 * Compliance settings and monitoring.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ComplianceSettings {
    
    @Column(name = "aml_enabled")
    @Builder.Default
    private boolean amlEnabled = true;
    
    @Column(name = "fraud_monitoring")
    @Builder.Default
    private boolean fraudMonitoring = true;
    
    @Column(name = "transaction_monitoring")
    @Builder.Default
    private boolean transactionMonitoring = true;
    
    @Column(name = "sanctions_screening")
    @Builder.Default
    private boolean sanctionsScreening = true;
    
    @Embedded
    private RiskThresholds riskThresholds;
}

/**
 * Risk monitoring thresholds.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RiskThresholds {
    
    @Column(name = "velocity_threshold", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal velocityThreshold = new BigDecimal("10000");
    
    @Column(name = "chargeback_threshold", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal chargebackThreshold = new BigDecimal("0.01");
    
    @Column(name = "fraud_threshold", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal fraudThreshold = new BigDecimal("0.005");
}

/**
 * API settings and access control.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ApiSettings {
    
    @Embedded
    private RateLimits rateLimits;
    
    @Column(name = "webhooks_enabled")
    @Builder.Default
    private boolean webhooksEnabled = true;
    
    @Column(name = "webhook_url")
    private String webhookUrl;
    
    @Column(name = "webhook_secret")
    private String webhookSecret;
    
    @Column(name = "api_version")
    @Builder.Default
    private String apiVersion = "v1";
    
    @Column(name = "api_key_id")
    private String apiKeyId;
}

/**
 * API rate limiting configuration.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class RateLimits {
    
    @Column(name = "requests_per_minute")
    @Builder.Default
    private Integer requestsPerMinute = 1000;
    
    @Column(name = "requests_per_hour")
    @Builder.Default
    private Integer requestsPerHour = 50000;
    
    @Column(name = "requests_per_day")
    @Builder.Default
    private Integer requestsPerDay = 1000000;
}

// Enums

/**
 * Business type enumeration.
 */
enum BusinessType {
    SOLE_PROPRIETORSHIP,
    PARTNERSHIP,
    LLC,
    CORPORATION,
    NON_PROFIT,
    GOVERNMENT,
    OTHER
}

/**
 * Industry classification.
 */
enum Industry {
    RETAIL,
    RESTAURANT,
    E_COMMERCE,
    PROFESSIONAL_SERVICES,
    HEALTHCARE,
    EDUCATION,
    AUTOMOTIVE,
    REAL_ESTATE,
    ENTERTAINMENT,
    TRAVEL,
    TECHNOLOGY,
    FINANCIAL_SERVICES,
    OTHER
}

/**
 * Merchant status enumeration.
 */
enum MerchantStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    CLOSED
}

/**
 * Merchant tier for fee optimization.
 */
enum MerchantTier {
    STANDARD,
    PREMIUM,
    ENTERPRISE
}

/**
 * Risk level assessment.
 */
enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Onboarding stage tracking.
 */
enum OnboardingStage {
    BASIC_INFO_COLLECTED,
    KYB_INITIATED,
    KYB_UNDER_REVIEW,
    PAYMENT_SETUP,
    STORE_SETUP,
    TESTING,
    COMPLETED
}

/**
 * KYB verification status.
 */
enum KYBStatus {
    PENDING,
    IN_PROGRESS,
    APPROVED,
    REJECTED,
    EXPIRED
}

/**
 * Settlement schedule options.
 */
enum SettlementSchedule {
    DAILY,
    WEEKLY,
    MONTHLY,
    ON_DEMAND
}