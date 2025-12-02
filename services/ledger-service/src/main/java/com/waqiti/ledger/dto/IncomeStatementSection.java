package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementSection {
    private String sectionName;
    private List<IncomeStatementLineItem> accounts;
    private List<IncomeStatementSection> subSections;
    private BigDecimal totalAmount;
    private BigDecimal percentageOfRevenue;
    private String currency;
}