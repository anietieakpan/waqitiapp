package com.waqiti.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthStatus {
    private boolean isHealthy;
    private long responseTime;
    private Instant timestamp;
    private String version;
    private String serviceName;
    private String error;
}