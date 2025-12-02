package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Merchant Spending DTO
 *
 * Represents spending metrics for a specific merchant.
 * Used for merchant loyalty analysis and spending pattern identification.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSpending {

    @NotBlank(message = "Merchant name cannot be blank")
    private String merchantName;

    @NotNull(message = "Total amount cannot be null")
    @PositiveOrZero(message = "Amount must be non-negative")
    private BigDecimal totalAmount;

    @PositiveOrZero(message = "Transaction count must be non-negative")
    private Integer transactionCount;

    private BigDecimal averageAmount;
    private BigDecimal percentageOfTotal;
    private String category;
    private String lastTransactionDate;
}
