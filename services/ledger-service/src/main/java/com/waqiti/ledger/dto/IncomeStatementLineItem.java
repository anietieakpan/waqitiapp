package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Income Statement Line Item DTO
 *
 * Represents individual line items in income statement sections
 * with account details, amounts, and analysis metrics.
 *
 * Supports hierarchical structure for subsections (e.g., Operating Expenses)
 * with proper accounting type classification for GAAP/IFRS compliance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStatementLineItem {

    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String itemName;  // Display name for line item (may differ from accountName for aggregated items)
    private String accountType;  // Account type classification (e.g., "SALES_REVENUE", "COST_OF_GOODS_SOLD")
    private BigDecimal amount;
    private BigDecimal percentageOfSales;
    private String notes;

    // For comparative analysis
    private BigDecimal priorPeriodAmount;
    private BigDecimal variance;
    private BigDecimal percentageChange;

    // Additional context
    private String currency;
    private Boolean isTotalLine;  // Indicates if this is a grand total line
    private Boolean isSubtotal;   // Indicates if this is a subtotal line (e.g., "Total Operating Expenses")
    private Integer indentLevel;

    // For hierarchical structure (subsections)
    private List<IncomeStatementLineItem> subItems;  // Nested line items (e.g., individual expenses under "Operating Expenses")
}