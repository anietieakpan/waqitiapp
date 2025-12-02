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
public class BalanceSheetSection {
    private String sectionName;
    private List<BalanceSheetLineItem> accounts;
    private List<BalanceSheetSection> subSections;
    private BigDecimal totalAmount;
    private String currency;
}