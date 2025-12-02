package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
public class FraudAnalysisResult {
    private String transactionId;
    private String userId;
    private FraudScore fraudScore;
    private FraudRiskLevel riskLevel;

    @Builder.Default
    private List<FraudRuleViolation> ruleViolations = new ArrayList<>();

    @Builder.Default
    private List<BehavioralAnomaly> behavioralAnomalies = new ArrayList<>();

    @Builder.Default
    private List<PatternMatch> patternMatches = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime analysisTimestamp;
    private String error;

    @Builder.Default
    private boolean escalated = false;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime escalationTimestamp;
}