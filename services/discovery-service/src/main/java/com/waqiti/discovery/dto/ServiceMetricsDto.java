package com.waqiti.discovery.dto;

import com.waqiti.discovery.domain.MetricsTimeframe;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Service Metrics DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMetricsDto {
    private String serviceName;
    private MetricsTimeframe timeframe;
    private Double averageResponseTime;
    private Double averageCpuUsage;
    private Double averageMemoryUsage;
    private Long totalRequests;
    private Long totalErrors;
    private Double errorRate;
    private Double uptime;
    private List<MetricsDataPoint> dataPoints;
    private Boolean noDataAvailable;
}
