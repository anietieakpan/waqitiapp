package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for manual review queue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualReviewRequest {
    private UUID depositId;
    private UUID userId;
    private BigDecimal amount;
    private BigDecimal riskScore;
    private String fraudIndicators;
    private String reason;
    private String priority;
    private String type = "CHECK_DEPOSIT";
}