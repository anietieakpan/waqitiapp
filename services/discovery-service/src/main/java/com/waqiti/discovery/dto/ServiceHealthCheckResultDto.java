package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.HealthStatus;
import com.waqiti.discovery.domain.InstanceHealthCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Service Health Check Result DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthCheckResultDto {
    private String serviceName;
    private Boolean overallHealthy;
    private HealthStatus healthStatus;
    private List<InstanceHealthCheckResult> instanceResults;
    private Instant timestamp;
}
