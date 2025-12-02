package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Auto Scaling Event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoScalingEvent {

    private String eventId;
    private String serviceName;
    private EventType eventType;
    private LocalDateTime timestamp;
    private ScalingAction scalingAction;
    private Integer currentInstances;
    private Integer targetInstances;
    private String triggerReason;
    private Map<String, Object> metrics;
    private String correlationId;
    private String scalingPolicy;
    private Boolean criticalService;
    private String failureReason;
    private String thresholdType;
    private Double currentUtilization;
    private Double thresholdValue;
    private String disabledReason;
    private String enabledReason;
    private java.util.List<String> terminatedInstances;

    public enum EventType {
        SCALE_UP_TRIGGERED,
        SCALE_DOWN_TRIGGERED,
        SCALE_UP_COMPLETED,
        SCALE_DOWN_COMPLETED,
        SCALING_FAILED,
        CAPACITY_THRESHOLD_REACHED,
        AUTO_SCALING_DISABLED,
        AUTO_SCALING_ENABLED
    }

    public enum ScalingAction {
        SCALE_UP,
        SCALE_DOWN,
        MAINTAIN,
        NONE
    }

    public boolean isCriticalService() {
        return criticalService != null && criticalService;
    }
}
