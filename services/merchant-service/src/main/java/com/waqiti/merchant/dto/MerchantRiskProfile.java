package com.waqiti.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for merchant risk profile data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRiskProfile {
    private UUID id;
    private String riskLevel;
    private BigDecimal riskScore;
    private LocalDateTime lastRiskAssessment;
    private BigDecimal totalVolume;
    private Long transactionCount;
    private BigDecimal chargebackRate;
}