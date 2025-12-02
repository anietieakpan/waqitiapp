package com.waqiti.bnpl.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for BNPL plan summary
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BnplPlanSummaryDto {

    private BigDecimal totalAmount;
    private BigDecimal totalPaid;
    private BigDecimal remainingBalance;
    private BigDecimal nextDueAmount;
    private LocalDate nextDueDate;
    private Integer completedInstallments;
    private Integer totalInstallments;
    private BigDecimal completionPercentage;
    private Boolean isOverdue;
    private BigDecimal overdueAmount;
}