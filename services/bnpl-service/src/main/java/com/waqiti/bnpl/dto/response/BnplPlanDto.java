package com.waqiti.bnpl.dto.response;

import com.waqiti.bnpl.domain.enums.BnplPlanStatus;
import com.waqiti.bnpl.domain.enums.PaymentFrequency;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for BNPL plan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplPlanDto {

    private Long id;
    private String planNumber;
    private String userId;
    private String merchantName;
    private BigDecimal purchaseAmount;
    private BigDecimal downPayment;
    private BigDecimal financeAmount;
    private BigDecimal totalAmount;
    private Integer numberOfInstallments;
    private BigDecimal installmentAmount;
    private PaymentFrequency paymentFrequency;
    private BnplPlanStatus status;
    private BigDecimal totalPaid;
    private BigDecimal remainingBalance;
    private LocalDate nextDueDate;
    private BigDecimal completionPercentage;
    private LocalDateTime createdAt;
}