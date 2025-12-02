package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IP reputation result with comprehensive threat detection
 */
@Data
@Builder
@Jacksonized
public class IpReputationResult {
    private String ipAddress;
    private double reputationScore;
    private boolean isBlacklisted;
    private boolean isMalicious;
    private boolean isSpam;
    private boolean isBotnet;
    private boolean isPhishing;
    private List<String> categories;
    private List<String> sources;
    private String lastThreatType;
    private int confidence;
    private LocalDateTime lastUpdated;
}