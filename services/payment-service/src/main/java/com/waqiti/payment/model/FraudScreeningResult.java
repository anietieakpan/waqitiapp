package com.waqiti.payment.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudScreeningResult {
    private String batchId;
    private Instant screeningStartTime;
    private Instant screeningEndTime;
    private Double batchRiskScore;
    private String batchRiskLevel;
    private List<String> suspiciousPatterns;
    private Integer highRiskPaymentCount;
    private List<PaymentFraudAlert> fraudAlerts;
    private Boolean blocked;

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
