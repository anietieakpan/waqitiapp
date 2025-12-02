package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "fee_calculations")
public class FeeCalculation {
    @Id
    private String id;
    private String eventId;
    private String transactionId;
    private String merchantId;
    private String userId;
    
    // Transaction details
    private BigDecimal transactionAmount;
    private String currency;
    private String transactionType;
    private String paymentMethod;
    private String provider;
    private String merchantCategory;
    
    // Fee components
    private BigDecimal processingFeePercentage;
    private BigDecimal processingFeeFixed;
    private BigDecimal processingFee;
    private BigDecimal platformFeePercentage;
    private BigDecimal platformFee;
    private BigDecimal providerFee;
    private BigDecimal crossBorderFee;
    private BigDecimal fxFee;
    private BigDecimal riskFee;
    private BigDecimal regulatoryFee;
    private BigDecimal subtotalFee;
    
    // Tiered pricing
    private FeeTier feeTier;
    private BigDecimal monthlyVolume;
    private BigDecimal tierDiscount;
    private BigDecimal tierDiscountPercentage;
    
    // Dynamic pricing
    private BigDecimal peakHourSurcharge;
    private BigDecimal promotionalDiscount;
    private String promotionCode;
    private BigDecimal loyaltyDiscount;
    
    // Override
    private boolean hasOverride;
    private BigDecimal overrideFee;
    private String overrideReason;
    private String overrideAppliedBy;
    private LocalDateTime overrideAppliedAt;
    
    // Final calculations
    private BigDecimal totalFee;
    private BigDecimal netAmount;
    private BigDecimal effectiveFeePercentage;
    private Map<String, BigDecimal> feeSplits;
    private BigDecimal platformRevenue;
    
    // Fee adjustments
    private boolean feeCapped;
    private String feeCapReason;
    private boolean feeAdjusted;
    private String feeAdjustmentReason;
    
    // Cross-border and FX
    private boolean crossBorder;
    private boolean fxApplied;
    private String sourceCurrency;
    private String targetCurrency;
    
    // Risk
    private Integer riskScore;
    
    // Status
    private FeeStatus status;
    private String failureReason;
    private LocalDateTime calculatedAt;
    private LocalDateTime calculationCompletedAt;
    
    // Metadata
    private String correlationId;
    private Long processingTimeMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

