package com.waqiti.expense.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Budget response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetResponse {

    private UUID id;

    private UUID userId;

    private String category;

    private BigDecimal budgetAmount;

    private BigDecimal spentAmount;

    private BigDecimal remainingAmount;

    private BudgetPeriod period;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private Boolean isActive;

    private Boolean isExceeded;

    private Double percentageUsed;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum BudgetPeriod {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
}
