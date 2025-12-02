package com.waqiti.common.performance;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Performance metrics data structure for storing test results
 */
@Data
@Builder
public class PerformanceMetrics {
    private String testName;
    private LocalDateTime timestamp;
    private double tps;
    private double averageLatencyMs;
    private double p95LatencyMs;
    private double successRate;
    private double cpuUsage;
    private long memoryUsage;
    private String testType;
    private int threadCount;
    private long totalRequests;
    private long failedRequests;
}