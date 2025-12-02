package com.waqiti.infrastructure.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapacityMetric {
    private String resource;
    private Double utilization;
    private Double threshold;
    private String region;
    private String zone;
    private LocalDateTime timestamp;
    private String unit;
    private Double available;
    private Double total;
    private String instanceId;
}