package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FraudDetectionResult {
    private boolean fraudulent;
    private double fraudScore;
    private String reason;
}