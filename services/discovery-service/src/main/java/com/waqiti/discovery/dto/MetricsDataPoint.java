package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metrics Data Point DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDataPoint {
    private Instant timestamp;
    private Double responseTime;
    private Double cpuUsage;
    private Double memoryUsage;
    private Long requestCount;
    private Long errorCount;
}
