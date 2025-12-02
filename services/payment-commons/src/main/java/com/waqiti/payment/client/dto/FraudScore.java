package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScore {
    private BigDecimal score;
    private String riskLevel;
    private String reason;
    private boolean shouldBlock;
    private Map<String, Object> details;

    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(riskLevel);
    }
}