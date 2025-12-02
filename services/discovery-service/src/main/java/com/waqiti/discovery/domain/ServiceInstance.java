package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service Instance Entity
 * Represents an individual instance of a registered service
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_instance",
    indexes = {
        @Index(name = "idx_service_instance_service", columnList = "service_id"),
        @Index(name = "idx_service_instance_status", columnList = "instance_status"),
        @Index(name = "idx_service_instance_health", columnList = "health_status"),
        @Index(name = "idx_service_instance_last_seen", columnList = "last_seen")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_instance_id", columnNames = "instance_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"serviceRegistry"})
@EqualsAndHashCode(callSuper = true, exclude = {"serviceRegistry"})
public class ServiceInstance extends BaseEntity {

    @NotBlank(message = "Instance ID is required")
    @Size(max = 100, message = "Instance ID must not exceed 100 characters")
    @Column(name = "instance_id", nullable = false, unique = true, length = 100)
    private String instanceId;

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", nullable = false, length = 100)
    private String serviceId;

    @NotBlank(message = "Instance host is required")
    @Size(max = 255, message = "Instance host must not exceed 255 characters")
    @Column(name = "instance_host", nullable = false, length = 255)
    private String instanceHost;

    @NotNull(message = "Instance port is required")
    @Min(value = 1, message = "Instance port must be at least 1")
    @Max(value = 65535, message = "Instance port must not exceed 65535")
    @Column(name = "instance_port", nullable = false)
    private Integer instancePort;

    @NotNull(message = "Instance status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "instance_status", nullable = false, length = 20)
    @Builder.Default
    private ServiceStatus instanceStatus = ServiceStatus.STARTING;

    @NotNull(message = "Health status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @Column(name = "last_health_check")
    private Instant lastHealthCheck;

    @Column(name = "consecutive_failures")
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @Column(name = "consecutive_successes")
    @Builder.Default
    private Integer consecutiveSuccesses = 0;

    @NotNull(message = "Registration timestamp is required")
    @Column(name = "registration_timestamp", nullable = false)
    @Builder.Default
    private Instant registrationTimestamp = Instant.now();

    @Column(name = "deregistration_timestamp")
    private Instant deregistrationTimestamp;

    @NotNull(message = "Last seen timestamp is required")
    @Column(name = "last_seen", nullable = false)
    @Builder.Default
    private Instant lastSeen = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instance_metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> instanceMetadata = new HashMap<>();

    @Min(value = 0, message = "Load balancer weight must not be negative")
    @Max(value = 1000, message = "Load balancer weight must not exceed 1000")
    @Column(name = "load_balancer_weight")
    @Builder.Default
    private Integer loadBalancerWeight = 100;

    @Column(name = "is_healthy")
    @Builder.Default
    private Boolean isHealthy = false;

    @Min(value = 0, message = "Response time must not be negative")
    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Min(value = 0, message = "CPU usage must not be negative")
    @Max(value = 100, message = "CPU usage must not exceed 100")
    @Column(name = "cpu_usage_percent", precision = 5, scale = 2)
    private Double cpuUsagePercent;

    @Min(value = 0, message = "Memory usage must not be negative")
    @Column(name = "memory_usage_mb")
    private Integer memoryUsageMb;

    @Min(value = 0, message = "Disk usage must not be negative")
    @Max(value = 100, message = "Disk usage must not exceed 100")
    @Column(name = "disk_usage_percent", precision = 5, scale = 2)
    private Double diskUsagePercent;

    @Min(value = 0, message = "Active connections must not be negative")
    @Column(name = "active_connections")
    @Builder.Default
    private Integer activeConnections = 0;

    @Min(value = 0, message = "Request count must not be negative")
    @Column(name = "request_count")
    @Builder.Default
    private Integer requestCount = 0;

    @Min(value = 0, message = "Error count must not be negative")
    @Column(name = "error_count")
    @Builder.Default
    private Integer errorCount = 0;

    @Min(value = 0, message = "Uptime must not be negative")
    @Column(name = "uptime_seconds")
    @Builder.Default
    private Integer uptimeSeconds = 0;

    @Size(max = 50, message = "Version must not exceed 50 characters")
    @Column(name = "version", length = 50)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "build_info", columnDefinition = "jsonb")
    private Map<String, Object> buildInfo;

    @Size(max = 100, message = "Deployment ID must not exceed 100 characters")
    @Column(name = "deployment_id", length = 100)
    private String deploymentId;

    @Size(max = 100, message = "Container ID must not exceed 100 characters")
    @Column(name = "container_id", length = 100)
    private String containerId;

    @Size(max = 100, message = "Node ID must not exceed 100 characters")
    @Column(name = "node_id", length = 100)
    private String nodeId;

    // Relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", referencedColumnName = "service_id", insertable = false, updatable = false)
    private ServiceRegistry serviceRegistry;

    // Business Methods

    /**
     * Update last seen timestamp
     */
    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }

    /**
     * Record successful health check
     */
    public void recordHealthCheckSuccess() {
        this.lastHealthCheck = Instant.now();
        this.consecutiveSuccesses++;
        this.consecutiveFailures = 0;
        this.healthStatus = HealthStatus.HEALTHY;
        this.isHealthy = true;
    }

    /**
     * Record failed health check
     */
    public void recordHealthCheckFailure() {
        this.lastHealthCheck = Instant.now();
        this.consecutiveFailures++;
        this.consecutiveSuccesses = 0;
        if (consecutiveFailures >= 3) {
            this.healthStatus = HealthStatus.UNHEALTHY;
            this.isHealthy = false;
        } else {
            this.healthStatus = HealthStatus.DEGRADED;
        }
    }

    /**
     * Check if instance should be removed due to consecutive failures
     *
     * @param threshold failure threshold
     * @return true if should be removed
     */
    public boolean shouldRemove(int threshold) {
        return consecutiveFailures >= threshold;
    }

    /**
     * Update metrics
     *
     * @param responseTime response time in ms
     * @param cpuUsage CPU usage percentage
     * @param memoryUsage memory usage in MB
     */
    public void updateMetrics(Integer responseTime, Double cpuUsage, Integer memoryUsage) {
        this.responseTimeMs = responseTime;
        this.cpuUsagePercent = cpuUsage;
        this.memoryUsageMb = memoryUsage;
        this.lastSeen = Instant.now();
    }

    /**
     * Increment request count
     */
    public void incrementRequestCount() {
        this.requestCount++;
    }

    /**
     * Increment error count
     */
    public void incrementErrorCount() {
        this.errorCount++;
    }

    /**
     * Get error rate percentage
     *
     * @return error rate (0-100)
     */
    public double getErrorRate() {
        if (requestCount == 0) {
            return 0.0;
        }
        return (double) errorCount / requestCount * 100.0;
    }

    /**
     * Check if instance is available for load balancing
     *
     * @return true if available
     */
    public boolean isAvailableForLoadBalancing() {
        return isHealthy && instanceStatus == ServiceStatus.UP
            && healthStatus == HealthStatus.HEALTHY;
    }

    /**
     * Get instance address
     *
     * @return host:port
     */
    public String getAddress() {
        return instanceHost + ":" + instancePort;
    }

    /**
     * Add metadata entry
     *
     * @param key metadata key
     * @param value metadata value
     */
    public void addMetadata(String key, Object value) {
        if (instanceMetadata == null) {
            instanceMetadata = new HashMap<>();
        }
        instanceMetadata.put(key, value);
    }

    /**
     * Deregister instance
     */
    public void deregister() {
        this.deregistrationTimestamp = Instant.now();
        this.instanceStatus = ServiceStatus.DOWN;
        this.isHealthy = false;
    }

    @PrePersist
    protected void onInstanceCreate() {
        if (registrationTimestamp == null) {
            registrationTimestamp = Instant.now();
        }
        if (lastSeen == null) {
            lastSeen = Instant.now();
        }
    }

    @PreUpdate
    protected void onInstanceUpdate() {
        // Automatically update last_seen on any update
        lastSeen = Instant.now();
    }
}
