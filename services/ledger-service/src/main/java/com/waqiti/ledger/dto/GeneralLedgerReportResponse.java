package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralLedgerReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<GeneralLedgerAccountReport> accounts;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private Map<String, BigDecimal> accountTypeTotals;
    private ReportMetadata metadata;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReportMetadata {
    private String reportName;
    private LocalDate generatedDate;
    private String generatedBy;
    private String reportPeriod;
    private int totalAccounts;
    private int totalTransactions;
}