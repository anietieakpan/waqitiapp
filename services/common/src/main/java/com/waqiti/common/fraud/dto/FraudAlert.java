package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import com.waqiti.common.fraud.model.AlertLevel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
public class FraudAlert {
    @NotBlank(message = "Alert ID is required")
    private String alertId;
    private AlertLevel level;
    private String transactionId;
    private String userId;
    private FraudScore fraudScore;
    private FraudRiskLevel riskLevel;

    @Builder.Default
    private List<FraudRuleViolation> violations = new ArrayList<>();

    @Builder.Default
    private List<BehavioralAnomaly> anomalies = new ArrayList<>();

    @Builder.Default
    private List<PatternMatch> patternMatches = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Builder.Default
    private boolean escalated = false;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime escalationTimestamp;
}