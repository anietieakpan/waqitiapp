package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Service Load Balancer Entity
 * Configuration for load balancing across service instances
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_load_balancer",
    indexes = {
        @Index(name = "idx_service_load_balancer_service", columnList = "service_id"),
        @Index(name = "idx_service_load_balancer_active", columnList = "is_active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_load_balancer_id", columnNames = "load_balancer_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(callSuper = true)
public class ServiceLoadBalancer extends BaseEntity {

    @NotBlank(message = "Load balancer ID is required")
    @Size(max = 100)
    @Column(name = "load_balancer_id", nullable = false, unique = true, length = 100)
    private String loadBalancerId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100)
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @NotBlank(message = "Load balancer name is required")
    @Size(max = 255)
    @Column(name = "load_balancer_name", nullable = false, length = 255)
    private String loadBalancerName;

    @NotNull(message = "Algorithm is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, length = 50)
    @Builder.Default
    private LoadBalancerStrategy algorithm = LoadBalancerStrategy.ROUND_ROBIN;

    @Column(name = "sticky_sessions")
    @Builder.Default
    private Boolean stickySessions = false;

    @Size(max = 50)
    @Column(name = "session_affinity_type", length = 50)
    private String sessionAffinityType;

    @Column(name = "health_check_enabled")
    @Builder.Default
    private Boolean healthCheckEnabled = true;

    @Column(name = "health_check_interval_seconds")
    @Builder.Default
    private Integer healthCheckIntervalSeconds = 30;

    @Column(name = "health_check_timeout_seconds")
    @Builder.Default
    private Integer healthCheckTimeoutSeconds = 10;

    @Column(name = "health_check_threshold")
    @Builder.Default
    private Integer healthCheckThreshold = 3;

    @Column(name = "unhealthy_threshold")
    @Builder.Default
    private Integer unhealthyThreshold = 3;

    @Column(name = "max_connections_per_instance")
    @Builder.Default
    private Integer maxConnectionsPerInstance = 1000;

    @Column(name = "connection_timeout_seconds")
    @Builder.Default
    private Integer connectionTimeoutSeconds = 30;

    @Column(name = "idle_timeout_seconds")
    @Builder.Default
    private Integer idleTimeoutSeconds = 60;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @NotBlank(message = "Created by is required")
    @Size(max = 100)
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    // Business Methods

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean requiresHealthCheck() {
        return healthCheckEnabled != null && healthCheckEnabled;
    }

    public boolean usesSessionAffinity() {
        return stickySessions != null && stickySessions;
    }
}
