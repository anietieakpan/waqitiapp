package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "currency_conversions")
public class CurrencyConversion {
    @Id
    private String id;
    private String eventId;
    private String referenceId;
    private String paymentId;
    private String userId;
    private String merchantId;
    
    // Currency details
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private BigDecimal actualTargetAmount;
    private BigDecimal netSourceAmount;
    private BigDecimal netTargetAmount;
    
    // Rate details
    private String rateProvider;
    private BigDecimal baseRate;
    private BigDecimal bidRate;
    private BigDecimal askRate;
    private BigDecimal finalRate;
    private BigDecimal executedRate;
    private LocalDateTime rateTimestamp;
    private Integer ratesFetched;
    private LocalDateTime ratesFetchedAt;
    
    // Spread and fees
    private BigDecimal spread;
    private BigDecimal markup;
    private BigDecimal platformFee;
    private BigDecimal effectiveSpread;
    private BigDecimal feeAmount;
    private BigDecimal savings;
    
    // Rate lock
    private boolean rateLocked;
    private LocalDateTime rateLockedAt;
    private LocalDateTime rateLockedUntil;
    private String rateLockReference;
    
    // Risk assessment
    private Integer riskScore;
    private String riskLevel;
    private BigDecimal riskAdjustment;
    private String riskAssessmentError;
    private boolean hedged;
    private String hedgeReference;
    private BigDecimal hedgeCost;
    
    // Execution
    private String executionId;
    private String executionReference;
    private LocalDateTime executedAt;
    private BigDecimal slippage;
    
    // Settlement
    private boolean settlementInitiated;
    private LocalDateTime settlementInitiatedAt;
    private String settlementError;
    
    // Validation
    private boolean limitsValidated;
    
    // Type and status
    private ConversionType conversionType;
    private ConversionStatus status;
    private String failureReason;
    private String purpose;
    
    // Timestamps
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private Long processingTimeMs;
    
    // Metadata
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

