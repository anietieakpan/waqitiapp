package com.waqiti.rewards.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for payment details from Payment Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailsDto {
    
    private String paymentId;
    private String userId;
    private String merchantId;
    private String merchantName;
    private String merchantCategory;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod; // CARD, BANK_TRANSFER, DIGITAL_WALLET
    private String paymentStatus; // PENDING, COMPLETED, FAILED, REFUNDED
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    
    // Payment details
    private String paymentReference;
    private String description;
    private String paymentProvider; // STRIPE, ADYEN, SQUARE, etc.
    private String providerTransactionId;
    
    // Card/Payment method details (masked)
    private String maskedCardNumber;
    private String cardType; // VISA, MASTERCARD, AMEX
    private String lastFourDigits;
    
    // Location and context
    private String merchantLocation;
    private String merchantCountry;
    private String transactionCountry;
    private String ipAddress; // For fraud detection context
    
    // Fees and breakdown
    private BigDecimal merchantFee;
    private BigDecimal processingFee;
    private BigDecimal netAmount;
    
    // Rewards eligibility
    private Boolean rewardsEligible;
    private String rewardsIneligibilityReason;
    private BigDecimal cashbackRate;
    private BigDecimal estimatedCashback;
    
    // Related transactions
    private List<String> relatedTransactionIds;
    private String originalPaymentId; // For refunds
    
    // Additional metadata
    private Map<String, Object> metadata;
}