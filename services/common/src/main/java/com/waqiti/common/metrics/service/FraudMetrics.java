package com.waqiti.common.metrics.service;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class FraudMetrics {
    private String result;
    private String riskLevel;
    private double riskScore;
    private Duration processingTime;
    private String blockedReason;
    private String fraudType;
    private String provider;
    private String countryCode;
    private boolean velocityCheck;
    private boolean ipReputationCheck;
    private boolean deviceFingerprintCheck;
}