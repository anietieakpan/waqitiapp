package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Service Metrics Entity
 * Stores performance and operational metrics for services
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_metrics",
    indexes = {
        @Index(name = "idx_service_metrics_service", columnList = "service_id"),
        @Index(name = "idx_service_metrics_instance", columnList = "instance_id"),
        @Index(name = "idx_service_metrics_name", columnList = "metric_name"),
        @Index(name = "idx_service_metrics_timestamp", columnList = "timestamp")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_metric_id", columnNames = "metric_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceMetrics extends BaseEntity {

    @NotBlank(message = "Metric ID is required")
    @Size(max = 100, message = "Metric ID must not exceed 100 characters")
    @Column(name = "metric_id", nullable = false, unique = true, length = 100)
    private String metricId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @Size(max = 100, message = "Instance ID must not exceed 100 characters")
    @Column(name = "instance_id", length = 100)
    private String instanceId;

    @NotBlank(message = "Metric name is required")
    @Size(max = 255, message = "Metric name must not exceed 255 characters")
    @Column(name = "metric_name", nullable = false, length = 255)
    private String metricName;

    @NotBlank(message = "Metric type is required")
    @Size(max = 50, message = "Metric type must not exceed 50 characters")
    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @NotNull(message = "Metric value is required")
    @Column(name = "metric_value", nullable = false, precision = 18, scale = 6)
    private Double metricValue;

    @Size(max = 50, message = "Metric unit must not exceed 50 characters")
    @Column(name = "metric_unit", length = 50)
    private String metricUnit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimensions", columnDefinition = "jsonb")
    private Map<String, Object> dimensions;

    @NotNull(message = "Timestamp is required")
    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Column(name = "retention_days")
    @Builder.Default
    private Integer retentionDays = 30;

    // Additional computed fields commonly used
    @Column(name = "average_response_time")
    private Double averageResponseTime;

    @Column(name = "cpu_usage")
    private Double cpuUsage;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "request_count")
    private Long requestCount;

    @Column(name = "error_count")
    private Long errorCount;

    /**
     * Calculate error rate
     *
     * @return error rate percentage (0-100)
     */
    public double calculateErrorRate() {
        if (requestCount == null || requestCount == 0) {
            return 0.0;
        }
        if (errorCount == null) {
            return 0.0;
        }
        return (double) errorCount / requestCount * 100.0;
    }

    /**
     * Check if metrics indicate healthy performance
     *
     * @return true if performance metrics are within acceptable ranges
     */
    public boolean isHealthyPerformance() {
        boolean cpuOk = cpuUsage == null || cpuUsage < 80.0;
        boolean memoryOk = memoryUsage == null || memoryUsage < 85.0;
        boolean errorRateOk = calculateErrorRate() < 5.0;
        return cpuOk && memoryOk && errorRateOk;
    }

    @PrePersist
    protected void onMetricsCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
