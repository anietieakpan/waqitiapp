package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {

    private UUID transactionId;
    private UUID accountId;
    private LocalDateTime transactionDate;
    private String transactionType; // DEBIT, CREDIT, TRANSFER
    private String category;
    private String subcategory;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String reference;
    private String counterpartyName;
    private String counterpartyAccount;
    private String paymentMethod;
    private String status;
    private BigDecimal fees;
    private BigDecimal exchangeRate;
    private String location;
    private String channel;
    private String merchantCode;
    private String authorizationCode;
    private Boolean isRecurring;
    private String riskLevel;
    private Double riskScore;
    private LocalDateTime settledDate;
    private BigDecimal runningBalance;

    // Additional metadata for reporting
    private String reportingCategory;
    private String regulatoryCode;
    private Boolean isReportable;
    private String tags;
}