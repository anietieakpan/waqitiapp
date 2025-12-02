package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Income Source DTO
 *
 * Represents a single income source with aggregated metrics.
 *
 * Common Income Sources:
 * - SALARY/PAYROLL
 * - FREELANCE/CONSULTING
 * - INVESTMENTS/DIVIDENDS
 * - BUSINESS INCOME
 * - RENTAL INCOME
 * - GOVERNMENT BENEFITS
 * - OTHER
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncomeSource {

    /**
     * Income source identifier/name
     */
    private String source;

    /**
     * Total amount received from this source
     */
    private BigDecimal amount;

    /**
     * Number of income transactions from this source
     */
    private Integer count;

    /**
     * Percentage of total income (0-100)
     */
    private BigDecimal percentage;

    /**
     * Average amount per transaction
     */
    private BigDecimal averageAmount;

    /**
     * Source type (SALARY, FREELANCE, INVESTMENT, etc.)
     */
    private String sourceType;

    /**
     * Frequency (WEEKLY, BI_WEEKLY, MONTHLY, IRREGULAR)
     */
    private String frequency;

    /**
     * Indicates if this is the primary income source
     */
    private Boolean isPrimary;
}
