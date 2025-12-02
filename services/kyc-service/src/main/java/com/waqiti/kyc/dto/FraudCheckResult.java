package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud Check Result DTO
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {
    private UUID userId;
    private String checkType;
    private LocalDateTime timestamp;

    @Builder.Default
    private BigDecimal riskScore = BigDecimal.ZERO;

    private String recommendedAction;
    private String errorMessage;

    @Builder.Default
    private Map<String, BigDecimal> checkScores = new HashMap<>();

    @Builder.Default
    private Map<String, Boolean> checkResults = new HashMap<>();

    public void addCheck(String checkName, boolean passed, BigDecimal score) {
        checkResults.put(checkName, passed);
        checkScores.put(checkName, score);
    }
}
