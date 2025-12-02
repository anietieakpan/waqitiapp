package com.waqiti.common.metrics.service;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class MetricsSummary {
    private boolean enabled;
    private String serviceName;
    private long activeTransactions;
    private long totalOperations;
    private long failedOperations;
    private int countersRegistered;
    private int timersRegistered;
    private int gaugesRegistered;
    private int summariesRegistered;
    private double cacheHitRate;
    private Instant lastUpdated;
}