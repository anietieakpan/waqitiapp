package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mobile Money Transfer Result DTO
 * 
 * Represents the result of a mobile money transfer operation
 * including status, fees, and settlement information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyTransferResult {

    private String transferId;

    private String status;

    private String transactionReference;

    private String providerTransactionId;

    private BigDecimal amount;

    private String currency;

    private String settlementCurrency;

    private BigDecimal settlementAmount;

    private BigDecimal exchangeRate;

    private BigDecimal totalFees;

    private Map<String, BigDecimal> feeBreakdown;

    private String failureReason;

    private String failureCode;

    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;

    private LocalDateTime expectedSettlement;

    private String receiptId;

    private Map<String, Object> providerMetadata;

    private String networkStatus;

    private String providerStatus;
}