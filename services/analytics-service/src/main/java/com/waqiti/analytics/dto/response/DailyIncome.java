package com.waqiti.analytics.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily Income DTO - Enhanced with Validation
 *
 * Represents income received on a specific date with comprehensive validation.
 * Used for income trend analysis and cash flow forecasting.
 *
 * Validation Rules:
 * - Date must not be null
 * - Amount must be zero or positive
 * - Transaction count must be zero or positive
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyIncome {

    /**
     * Date of income receipt (required)
     */
    @NotNull(message = "Income date cannot be null")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * Total income amount received on this date (must be >= 0)
     */
    @NotNull(message = "Income amount cannot be null")
    @PositiveOrZero(message = "Income amount must be zero or positive")
    private BigDecimal amount;

    /**
     * Number of income transactions (must be >= 0)
     */
    @PositiveOrZero(message = "Transaction count must be zero or positive")
    private Integer transactionCount;

    /**
     * Day of week (MONDAY, TUESDAY, etc.)
     */
    private String dayOfWeek;

    /**
     * Comma-separated income sources on this date
     */
    private String sources;

    /**
     * Helper method: Check if income was received on this date
     */
    public boolean hasIncome() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Helper method: Get formatted date string
     */
    public String getFormattedDate() {
        return date != null ? date.toString() : "N/A";
    }
}
