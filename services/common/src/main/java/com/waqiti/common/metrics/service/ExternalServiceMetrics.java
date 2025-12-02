package com.waqiti.common.metrics.service;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class ExternalServiceMetrics {
    private String serviceName;
    private String operation;
    private String status;
    private int responseCode;
    private Duration responseTime;
    private boolean circuitBreakerTriggered;
    private int retryCount;
    private String errorType;
    private String region;
    private String provider;
}