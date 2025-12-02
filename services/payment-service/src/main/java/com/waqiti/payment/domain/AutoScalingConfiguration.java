package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Auto Scaling Configuration Entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "auto_scaling_configurations")
public class AutoScalingConfiguration {

    @Id
    private String id;

    private String serviceName;
    private Integer minInstances;
    private Integer maxInstances;
    private Integer targetCpuUtilization;
    private Integer targetMemoryUtilization;
    private Integer scaleUpThreshold;
    private Integer scaleDownThreshold;
    private Integer cooldownPeriodSeconds;
    private String scalingPolicy;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
