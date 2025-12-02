package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Scaling trigger domain entity
 */
@Entity
@Table(name = "scaling_triggers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScalingTrigger {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String triggerType;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    private double currentValue;

    @Column(nullable = false)
    private double thresholdValue;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime triggeredAt = LocalDateTime.now();

    private String correlationId;

    private String severity;

    @Column(nullable = false)
    private boolean shouldScale;

    private int targetInstances;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    private LocalDateTime processedAt;

    private String processedBy;

    @Column(length = 1000)
    private String notes;
}
