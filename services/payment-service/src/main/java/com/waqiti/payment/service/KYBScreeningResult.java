package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class KYBScreeningResult {
    private String screeningId;
    private UUID businessId;
    private int overallRiskScore;
    private RiskLevel riskLevel;
    private boolean passed;
    private List<String> findings;
    private boolean sanctions;
    private boolean pep;
    private boolean adverseMedia;
    private List<String> recommendedActions;
    private Instant completedAt;
}

