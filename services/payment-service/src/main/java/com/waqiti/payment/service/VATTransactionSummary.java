package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class VATTransactionSummary {
    private String transactionId;
    private VATTransactionType transactionType;
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private BigDecimal vatRate;
    private String description;
    private LocalDate transactionDate;
    private String supplierName;
    private String supplierVATNumber;
}

