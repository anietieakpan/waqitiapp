package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;

/**
 * Discovery Statistics Entity
 * Daily/periodic statistics for the discovery service
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "discovery_statistics",
    indexes = {
        @Index(name = "idx_discovery_statistics_period", columnList = "period_end")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_discovery_period", columnNames = {"period_start", "period_end"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class DiscoveryStatistics extends BaseEntity {

    @NotNull(message = "Period start is required")
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @NotNull(message = "Period end is required")
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Min(value = 0)
    @Column(name = "total_registered_services")
    @Builder.Default
    private Integer totalRegisteredServices = 0;

    @Min(value = 0)
    @Column(name = "active_service_instances")
    @Builder.Default
    private Integer activeServiceInstances = 0;

    @Min(value = 0)
    @Column(name = "health_checks_performed")
    @Builder.Default
    private Long healthChecksPerformed = 0L;

    @Min(value = 0)
    @Column(name = "successful_health_checks")
    @Builder.Default
    private Long successfulHealthChecks = 0L;

    @Min(value = 0)
    @Column(name = "failed_health_checks")
    @Builder.Default
    private Long failedHealthChecks = 0L;

    @Column(name = "avg_service_uptime_hours", precision = 10, scale = 2)
    private Double avgServiceUptimeHours;

    @Min(value = 0)
    @Column(name = "service_discovery_requests")
    @Builder.Default
    private Long serviceDiscoveryRequests = 0L;

    @Min(value = 0)
    @Column(name = "load_balancer_decisions")
    @Builder.Default
    private Long loadBalancerDecisions = 0L;

    @Min(value = 0)
    @Column(name = "circuit_breaker_activations")
    @Builder.Default
    private Integer circuitBreakerActivations = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "by_service_category", columnDefinition = "jsonb")
    private Map<String, Object> byServiceCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "by_region", columnDefinition = "jsonb")
    private Map<String, Object> byRegion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_unhealthy_services", columnDefinition = "jsonb")
    private Map<String, Object> topUnhealthyServices;

    // Business Methods

    public double getHealthCheckSuccessRate() {
        if (healthChecksPerformed == 0) {
            return 0.0;
        }
        return (double) successfulHealthChecks / healthChecksPerformed * 100.0;
    }

    public double getHealthCheckFailureRate() {
        return 100.0 - getHealthCheckSuccessRate();
    }

    public boolean isPerformanceGood() {
        return getHealthCheckSuccessRate() >= 95.0;
    }
}
