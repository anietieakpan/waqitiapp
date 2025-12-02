package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;

/**
 * Blacklist check result
 */
@Data
@Builder
@Jacksonized
public class BlacklistCheckResult {
    private String checkType;
    private boolean isBlacklisted;
    private String reason;
    private List<String> matchingSources;
    private List<BlacklistMatch> matches;
    private BlacklistRiskScore riskScore;
    private double confidence;
    private String severity;
    private Instant checkTimestamp;
    private String recommendation;
    private boolean shouldBlock;
    private List<String> actions;
}