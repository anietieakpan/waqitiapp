package com.waqiti.transaction.dto;

import lombok.Data;
import java.util.List;

@Data
public class ComplianceCheckResponse {
    private boolean approved;
    private String status;
    private double riskScore;
    private List<String> flags;
    private String reason;
    private boolean requiresManualReview;
}