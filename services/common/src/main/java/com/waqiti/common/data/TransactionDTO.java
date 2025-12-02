package com.waqiti.common.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Transaction Data Transfer Object for optimized queries
 * Includes all related data to avoid N+1 queries
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {
    
    // Transaction core fields
    private String id;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String type;
    private String reference;
    private String description;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    // User information (to avoid separate user query)
    private String userId;
    private String userFirstName;
    private String userLastName;
    private String userEmail;
    private String userPhone;
    
    // Account information (to avoid separate account query)
    private String accountId;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    
    // Payment method information (to avoid separate payment method query)
    private String paymentMethodId;
    private String paymentMethod;
    private String lastFourDigits;
    private String cardBrand;
    private String cardType;
    
    // Merchant information (for payment transactions)
    private String merchantId;
    private String merchantName;
    private String merchantCategory;
    private String merchantCountry;
    
    // Financial details
    private BigDecimal fee;
    private String feeType;
    private BigDecimal netAmount;
    private BigDecimal exchangeRate;
    private String originalCurrency;
    private BigDecimal originalAmount;
    
    // Risk and fraud information
    private String riskScore;
    private String riskLevel;
    private List<String> riskFlags;
    private boolean flaggedForReview;
    
    // Location information
    private String ipAddress;
    private String country;
    private String city;
    private String timezone;
    
    // Processing information
    private String processingBank;
    private String authorizationCode;
    private String processorResponse;
    private String processorTransactionId;
    
    // Compliance and audit
    private boolean amlChecked;
    private boolean sanctionsChecked;
    private String complianceStatus;
    private Map<String, Object> auditTrail;
    
    // Related transaction information
    private String parentTransactionId;
    private List<String> childTransactionIds;
    private String settlementId;
    private String reconciliationId;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private List<String> tags;
    private String notes;
    
    /**
     * Constructor for JPQL projection with essential fields
     */
    public TransactionDTO(String id, BigDecimal amount, String currency, String status, LocalDateTime createdAt,
                         String userFirstName, String userLastName, String userEmail,
                         String accountNumber, String accountType,
                         String paymentMethod, String lastFourDigits) {
        this.id = id;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.userFirstName = userFirstName;
        this.userLastName = userLastName;
        this.userEmail = userEmail;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.paymentMethod = paymentMethod;
        this.lastFourDigits = lastFourDigits;
    }
    
    /**
     * Get the full user name
     */
    public String getUserFullName() {
        if (userFirstName != null && userLastName != null) {
            return userFirstName + " " + userLastName;
        }
        return userFirstName != null ? userFirstName : userLastName;
    }
    
    /**
     * Check if transaction is successful
     */
    public boolean isSuccessful() {
        return "COMPLETED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status);
    }
    
    /**
     * Check if transaction requires manual review
     */
    public boolean requiresReview() {
        return flaggedForReview || "REVIEW".equalsIgnoreCase(status) || 
               "PENDING_REVIEW".equalsIgnoreCase(status);
    }
    
    /**
     * Get transaction amount in base currency units (cents)
     */
    public Long getAmountInCents() {
        return amount != null ? amount.multiply(BigDecimal.valueOf(100)).longValue() : 0L;
    }
    
    /**
     * Check if transaction is international
     */
    public boolean isInternational() {
        return originalCurrency != null && !originalCurrency.equals(currency);
    }
    
    /**
     * Get effective amount (net amount if available, otherwise gross amount)
     */
    public BigDecimal getEffectiveAmount() {
        return netAmount != null ? netAmount : amount;
    }
}