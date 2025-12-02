package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Auto-scaling trigger event
 * Published when scaling thresholds are exceeded
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoScalingTriggerEvent {

    private String serviceName;

    private String triggerType;

    private String metricName;

    private double currentValue;

    private double thresholdValue;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String correlationId;

    private String severity;

    private boolean shouldScale;

    private int targetInstances;

    private String queueName;

    private String customMetricName;
}
