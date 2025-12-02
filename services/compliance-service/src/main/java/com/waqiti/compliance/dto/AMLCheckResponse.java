package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * AML Check Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLCheckResponse {
    private String checkId;
    private UUID userId;
    private UUID transactionId;
    private BigDecimal riskScore;
    private Boolean flagged;
    private String reason;
    private String status;
    private String recommendedAction;
}
