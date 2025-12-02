package com.waqiti.merchant.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.waqiti.merchant.enums.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core Merchant Entity
 * Represents a business entity that accepts payments through the platform
 */
@Entity
@Table(name = "merchants", indexes = {
    @Index(name = "idx_merchant_code", columnList = "merchantCode", unique = true),
    @Index(name = "idx_merchant_email", columnList = "email"),
    @Index(name = "idx_merchant_status", columnList = "status"),
    @Index(name = "idx_merchant_category", columnList = "category"),
    @Index(name = "idx_merchant_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Merchant {
    
    @Id
    @EqualsAndHashCode.Include
    private String id;
    
    @Column(nullable = false, unique = true, length = 20)
    @NotBlank
    private String merchantCode;
    
    @Column(nullable = false)
    @NotBlank
    private String businessName;
    
    @Column(nullable = false)
    @NotBlank
    private String legalName;
    
    @Column(unique = true)
    private String registrationNumber;
    
    @Column(unique = true)
    private String taxId;
    
    @Column(nullable = false)
    @Email
    @NotBlank
    private String email;
    
    @Column(nullable = false)
    @NotBlank
    private String phoneNumber;
    
    private String alternatePhone;
    
    @Column(nullable = false)
    @NotBlank
    private String country;
    
    @Column(nullable = false)
    @NotBlank
    private String city;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    private String postalCode;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private MerchantStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private MerchantCategory category;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private MerchantTier tier;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;
    
    @Column(nullable = false)
    private boolean isActive;
    
    private LocalDateTime activatedAt;
    
    private LocalDateTime deactivatedAt;
    
    @Column(nullable = false)
    private boolean kycCompleted;
    
    private LocalDateTime kycCompletedAt;
    
    @Column(columnDefinition = "TEXT")
    private String websiteUrl;
    
    @Column(columnDefinition = "TEXT")
    private String logoUrl;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    // Financial Information
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal settlementBalance;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalProcessed;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyLimit;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal transactionLimit;
    
    // Fee Configuration
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal transactionFeePercentage;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal transactionFeeFixed;
    
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal settlementFeePercentage;
    
    @Column(nullable = false)
    private String defaultCurrency;
    
    @ElementCollection
    @CollectionTable(name = "merchant_supported_currencies")
    private Set<String> supportedCurrencies;
    
    // Settlement Configuration
    @Enumerated(EnumType.STRING)
    private SettlementFrequency settlementFrequency;
    
    @Column(nullable = false)
    private int settlementDelay; // in days
    
    private String bankAccountId;
    
    private String preferredSettlementMethod;
    
    // Security
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonIgnore
    private String apiKey;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonIgnore
    private String secretKey;

    private String webhookUrl;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @JsonIgnore
    private String webhookSecret;
    
    @ElementCollection
    @CollectionTable(name = "merchant_ip_whitelist")
    private Set<String> ipWhitelist;
    
    // Risk Management
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal riskScore;
    
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;
    
    @Column(nullable = false)
    private int chargebackCount;
    
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal chargebackRate;
    
    @Column(nullable = false)
    private int fraudCount;
    
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal fraudRate;
    
    // Compliance
    private boolean amlVerified;
    
    private LocalDateTime amlVerifiedAt;
    
    private String amlRiskRating;
    
    private boolean sanctionsChecked;
    
    private LocalDateTime sanctionsCheckedAt;
    
    private String complianceNotes;
    
    // Contact Information
    private String primaryContactName;
    
    private String primaryContactEmail;
    
    private String primaryContactPhone;
    
    private String technicalContactEmail;
    
    private String financialContactEmail;
    
    // Features and Permissions
    @ElementCollection
    @CollectionTable(name = "merchant_features")
    private Set<String> enabledFeatures;
    
    @ElementCollection
    @CollectionTable(name = "merchant_payment_methods")
    private Set<String> allowedPaymentMethods;
    
    private boolean canAcceptInternational;
    
    private boolean canProcessRefunds;
    
    private boolean canProcessRecurring;
    
    private boolean canStoreCreditCards;
    
    // Integration
    private String integrationMethod; // API, SDK, PLUGIN
    
    private String platformVersion;
    
    private LocalDateTime lastApiCall;
    
    private Long apiCallCount;
    
    // Documents
    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MerchantDocument> documents;
    
    // Bank Accounts
    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MerchantBankAccount> bankAccounts;
    
    // API Keys
    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MerchantApiKey> apiKeys;
    
    // Metadata
    @ElementCollection
    @CollectionTable(name = "merchant_metadata")
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata;
    
    // Audit fields
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    private String createdBy;
    
    private String updatedBy;
    
    private LocalDateTime lastLoginAt;
    
    private String lastLoginIp;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.merchantCode == null) {
            this.merchantCode = generateMerchantCode();
        }
        if (this.status == null) {
            this.status = MerchantStatus.PENDING_VERIFICATION;
        }
        if (this.tier == null) {
            this.tier = MerchantTier.STARTER;
        }
        if (this.verificationStatus == null) {
            this.verificationStatus = VerificationStatus.PENDING;
        }
        if (this.riskLevel == null) {
            this.riskLevel = RiskLevel.MEDIUM;
        }
        if (this.settlementFrequency == null) {
            this.settlementFrequency = SettlementFrequency.DAILY;
        }
        if (this.settlementBalance == null) {
            this.settlementBalance = BigDecimal.ZERO;
        }
        if (this.pendingBalance == null) {
            this.pendingBalance = BigDecimal.ZERO;
        }
        if (this.totalProcessed == null) {
            this.totalProcessed = BigDecimal.ZERO;
        }
        if (this.riskScore == null) {
            this.riskScore = new BigDecimal("0.5");
        }
        if (this.chargebackCount == 0) {
            this.chargebackCount = 0;
        }
        if (this.chargebackRate == null) {
            this.chargebackRate = BigDecimal.ZERO;
        }
        if (this.fraudCount == 0) {
            this.fraudCount = 0;
        }
        if (this.fraudRate == null) {
            this.fraudRate = BigDecimal.ZERO;
        }
        this.isActive = false;
        this.kycCompleted = false;
        this.amlVerified = false;
        this.sanctionsChecked = false;
    }
    
    private String generateMerchantCode() {
        return "MER" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000);
    }
}