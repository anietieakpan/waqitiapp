package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compliance Result DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceResult {
    private boolean approved;
    private String reason;
    private String complianceCheckId;
}