package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VATReport {
    private UUID reportId;
    private UUID businessProfileId;
    private String businessName;
    private String vatNumber;
    private int quarter;
    private int year;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalSales;
    private BigDecimal totalVATCollected;
    private BigDecimal totalPurchases;
    private BigDecimal totalVATPaid;
    private BigDecimal vatDue;
    private String documentUrl;
    private List<VATTransactionSummary> transactions;
    private Instant generatedAt;
    private String status;
}