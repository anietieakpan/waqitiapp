package com.waqiti.bnpl.dto.response;

import com.waqiti.bnpl.domain.enums.InstallmentStatus;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for BNPL installment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplInstallmentDto {

    private Long id;
    private Integer installmentNumber;
    private LocalDate dueDate;
    private BigDecimal amount;
    private BigDecimal amountPaid;
    private BigDecimal amountDue;
    private InstallmentStatus status;
    private LocalDateTime paidDate;
    private boolean isOverdue;
    private Integer daysOverdue;
}