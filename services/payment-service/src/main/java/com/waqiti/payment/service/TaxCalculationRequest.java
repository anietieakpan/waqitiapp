package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class TaxCalculationRequest {
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String country;
    private String state;
    private String transactionType;
    private LocalDate transactionDate;
    private BigDecimal transactionFee;
    private List<String> taxExemptionCodes;
    private String transactionCategory;
    private String recipientType;
}