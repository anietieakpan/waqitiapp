package com.waqiti.frauddetection.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionResult {

    private String transactionId;
    private FraudDecision decision;
    private BigDecimal riskScore;
    private Double confidence;
    private List<String> reasons;
    private String modelVersion;
    private Long processingTimeMs;
    private LocalDateTime timestamp;
}