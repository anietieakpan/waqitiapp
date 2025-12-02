package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Discovery Analytics Entity
 * Aggregated analytics for the discovery service
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "discovery_analytics",
    indexes = {
        @Index(name = "idx_discovery_analytics_period", columnList = "period_end")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_analytics_id", columnNames = "analytics_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class DiscoveryAnalytics extends BaseEntity {

    @NotBlank(message = "Analytics ID is required")
    @Size(max = 100)
    @Column(name = "analytics_id", nullable = false, unique = true, length = 100)
    private String analyticsId;

    @NotNull(message = "Period start is required")
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @NotNull(message = "Period end is required")
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Min(value = 0)
    @Column(name = "total_services")
    @Builder.Default
    private Integer totalServices = 0;

    @Min(value = 0)
    @Column(name = "healthy_services")
    @Builder.Default
    private Integer healthyServices = 0;

    @Min(value = 0)
    @Column(name = "unhealthy_services")
    @Builder.Default
    private Integer unhealthyServices = 0;

    @Min(value = 0)
    @Column(name = "total_instances")
    @Builder.Default
    private Integer totalInstances = 0;

    @Min(value = 0)
    @Column(name = "healthy_instances")
    @Builder.Default
    private Integer healthyInstances = 0;

    @Column(name = "average_response_time_ms", precision = 10, scale = 2)
    private Double averageResponseTimeMs;

    @Column(name = "service_availability", precision = 5, scale = 4)
    private Double serviceAvailability;

    @Column(name = "health_check_success_rate", precision = 5, scale = 4)
    private Double healthCheckSuccessRate;

    @Min(value = 0)
    @Column(name = "circuit_breaker_trips")
    @Builder.Default
    private Integer circuitBreakerTrips = 0;

    @Min(value = 0)
    @Column(name = "service_registrations")
    @Builder.Default
    private Integer serviceRegistrations = 0;

    @Min(value = 0)
    @Column(name = "service_deregistrations")
    @Builder.Default
    private Integer serviceDeregistrations = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "by_service_type", columnDefinition = "jsonb")
    private Map<String, Object> byServiceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "by_environment", columnDefinition = "jsonb")
    private Map<String, Object> byEnvironment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "by_zone", columnDefinition = "jsonb")
    private Map<String, Object> byZone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "performance_trends", columnDefinition = "jsonb")
    private Map<String, Object> performanceTrends;

    // Business Methods

    public double getHealthyServicePercentage() {
        if (totalServices == 0) {
            return 0.0;
        }
        return (double) healthyServices / totalServices * 100.0;
    }

    public double getHealthyInstancePercentage() {
        if (totalInstances == 0) {
            return 0.0;
        }
        return (double) healthyInstances / totalInstances * 100.0;
    }

    public boolean isSystemHealthy() {
        return getHealthyServicePercentage() >= 80.0 && getHealthyInstancePercentage() >= 80.0;
    }
}
