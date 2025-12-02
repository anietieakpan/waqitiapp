package com.waqiti.bnpl.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Response DTO for BNPL plan details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BnplPlanDetailsDto {

    private BnplPlanDto plan;
    private List<BnplInstallmentDto> installments;
    private List<BnplTransactionDto> transactions;
    private BnplPlanSummaryDto summary;
}