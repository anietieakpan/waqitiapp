package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;

/**
 * Service Health Entity
 * Tracks health check history for services
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_health_check",
    indexes = {
        @Index(name = "idx_service_health_check_service", columnList = "service_id"),
        @Index(name = "idx_service_health_check_instance", columnList = "instance_id"),
        @Index(name = "idx_service_health_check_status", columnList = "check_status"),
        @Index(name = "idx_service_health_check_last_check", columnList = "last_check_time")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_check_id", columnNames = "check_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceHealth extends BaseEntity {

    @NotBlank(message = "Check ID is required")
    @Size(max = 100, message = "Check ID must not exceed 100 characters")
    @Column(name = "check_id", nullable = false, unique = true, length = 100)
    private String checkId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @NotBlank(message = "Instance ID is required")
    @Size(max = 100, message = "Instance ID must not exceed 100 characters")
    @Column(name = "instance_id", nullable = false, length = 100)
    private String instanceId;

    @NotBlank(message = "Check name is required")
    @Size(max = 255, message = "Check name must not exceed 255 characters")
    @Column(name = "check_name", nullable = false, length = 255)
    private String checkName;

    @NotBlank(message = "Check type is required")
    @Size(max = 50, message = "Check type must not exceed 50 characters")
    @Column(name = "check_type", nullable = false, length = 50)
    private String checkType;

    @Size(max = 1000, message = "Check URL must not exceed 1000 characters")
    @Column(name = "check_url", length = 1000)
    private String checkUrl;

    @Size(max = 10, message = "Check method must not exceed 10 characters")
    @Column(name = "check_method", length = 10)
    @Builder.Default
    private String checkMethod = "GET";

    @Column(name = "timeout_seconds")
    @Builder.Default
    private Integer timeoutSeconds = 10;

    @Column(name = "interval_seconds")
    @Builder.Default
    private Integer intervalSeconds = 30;

    @NotNull(message = "Check status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "check_status", nullable = false, length = 20)
    @Builder.Default
    private HealthStatus status = HealthStatus.UNKNOWN;

    @Column(name = "last_check_time")
    private Instant checkTime;

    @Column(name = "response_time_ms")
    private Integer responseTime;

    @Column(name = "error_rate", precision = 5, scale = 4)
    private Double errorRate;

    @Min(value = 0, message = "Healthy instances must not be negative")
    @Column(name = "healthy_instances")
    private Integer healthyInstances;

    @Min(value = 0, message = "Total instances must not be negative")
    @Column(name = "total_instances")
    private Integer totalInstances;

    /**
     * Calculate health percentage
     *
     * @return health percentage (0-100)
     */
    public double getHealthPercentage() {
        if (totalInstances == null || totalInstances == 0) {
            return 0.0;
        }
        return (double) healthyInstances / totalInstances * 100.0;
    }

    /**
     * Check if service is healthy
     *
     * @return true if at least 50% of instances are healthy
     */
    public boolean isServiceHealthy() {
        return getHealthPercentage() >= 50.0;
    }
}
