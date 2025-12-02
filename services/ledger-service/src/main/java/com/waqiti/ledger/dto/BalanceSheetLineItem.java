package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Balance Sheet Line Item DTO
 *
 * Represents individual line items in balance sheet sections
 * with account details, amounts, and analysis metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSheetLineItem {

    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String itemName;
    private String accountType;
    private BigDecimal amount;
    private BigDecimal percentage;
    private String notes;

    // For detailed analysis
    private BigDecimal priorPeriodAmount;
    private BigDecimal variance;
    private BigDecimal percentageChange;

    // Additional context
    private String currency;
    private Boolean isTotalLine;
    private Boolean isSubtotal;
    private Integer indentLevel;

    // For hierarchical structure
    private List<BalanceSheetLineItem> subItems;
}