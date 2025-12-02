package com.waqiti.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for Merchant Details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantDetailDTO {
    
    private UUID merchantId;
    private String merchantName;
    private String businessName;
    private String merchantCode;
    private String category;
    private String subCategory;
    private String status;
    private String tier;
    
    // Contact information
    private String email;
    private String phoneNumber;
    private String website;
    private String supportEmail;
    private String supportPhone;
    
    // Address information
    private String streetAddress;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String timezone;
    
    // Business details
    private String businessType;
    private String registrationNumber;
    private String taxId;
    private LocalDateTime establishedDate;
    private String industryCode;
    private Integer employeeCount;
    private String businessDescription;
    
    // Banking information
    private String bankName;
    private String accountNumber; // Masked
    private String routingNumber;
    private String swiftCode;
    private String defaultCurrency;
    private List<String> supportedCurrencies;
    
    // Transaction statistics
    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal averageTransactionAmount;
    private Long totalTransactions;
    private Long monthlyTransactions;
    private BigDecimal refundRate;
    private BigDecimal chargebackRate;
    
    // Payment configuration
    private List<String> acceptedPaymentMethods;
    private BigDecimal transactionFeePercentage;
    private BigDecimal flatTransactionFee;
    private BigDecimal minimumTransactionAmount;
    private BigDecimal maximumTransactionAmount;
    private String settlementSchedule;
    private Integer settlementDelayDays;
    
    // Risk and compliance
    private String riskLevel;
    private String kycStatus;
    private String complianceStatus;
    private LocalDateTime lastComplianceCheck;
    private BigDecimal riskScore;
    private List<String> riskFlags;
    private boolean isHighRisk;
    private boolean requiresManualReview;
    
    // Features and capabilities
    private boolean canProcessRefunds;
    private boolean canProcessRecurring;
    private boolean canStoreCards;
    private boolean canProcessInternational;
    private boolean hasApiAccess;
    private boolean hasWebhooks;
    
    // Limits
    private BigDecimal dailyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;
    private BigDecimal singleTransactionLimit;
    private Integer dailyTransactionCount;
    private Integer monthlyTransactionCount;
    
    // Integration details
    private String apiKey; // Masked
    private String webhookUrl;
    private String callbackUrl;
    private String integrationMethod;
    private LocalDateTime integrationDate;
    private String platformVersion;
    
    // Performance metrics
    private Double successRate;
    private Double averageProcessingTime;
    private Double uptime;
    private Long failedTransactions;
    private LocalDateTime lastDowntime;
    
    // Audit information
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private boolean isActive;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private String verifiedBy;
    
    // Additional metadata
    private Map<String, Object> customAttributes;
    private List<String> tags;
    private String notes;
    
    /**
     * Check if merchant is active and verified
     */
    public boolean isOperational() {
        return isActive && isVerified && "ACTIVE".equals(status);
    }
    
    /**
     * Check if merchant can process amount
     */
    public boolean canProcessAmount(BigDecimal amount) {
        if (!isOperational()) {
            return false;
        }
        
        if (minimumTransactionAmount != null && amount.compareTo(minimumTransactionAmount) < 0) {
            return false;
        }
        
        if (maximumTransactionAmount != null && amount.compareTo(maximumTransactionAmount) > 0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculate net amount after fees
     */
    public BigDecimal calculateNetAmount(BigDecimal amount) {
        BigDecimal fees = BigDecimal.ZERO;

        if (transactionFeePercentage != null) {
            fees = fees.add(amount.multiply(transactionFeePercentage.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP)));
        }
        
        if (flatTransactionFee != null) {
            fees = fees.add(flatTransactionFee);
        }
        
        return amount.subtract(fees);
    }
    
    /**
     * Check if merchant needs compliance review
     */
    public boolean needsComplianceReview() {
        return isHighRisk || 
               requiresManualReview || 
               "PENDING".equals(complianceStatus) ||
               (riskScore != null && riskScore.compareTo(new BigDecimal("70")) > 0);
    }
    
    /**
     * Get merchant ID as String for compatibility
     */
    public String getId() {
        return merchantId != null ? merchantId.toString() : null;
    }
}