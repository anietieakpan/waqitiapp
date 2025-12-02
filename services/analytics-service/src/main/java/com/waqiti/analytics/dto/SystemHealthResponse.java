package com.waqiti.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for system health metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthResponse {
    
    private String status; // HEALTHY, DEGRADED, CRITICAL
    private Long totalTransactions;
    private BigDecimal totalVolume;
    private Integer criticalAnomalies;
    private Integer activeAlerts;
    private Double systemLoad;
    private String version;
    private Instant lastUpdated;
}