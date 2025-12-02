package com.waqiti.discovery.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Service Registry Entity
 * Represents a registered service in the discovery system
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Entity
@Table(name = "service_registry",
    indexes = {
        @Index(name = "idx_service_registry_name", columnList = "service_name"),
        @Index(name = "idx_service_registry_type", columnList = "service_type"),
        @Index(name = "idx_service_registry_status", columnList = "service_status"),
        @Index(name = "idx_service_registry_environment", columnList = "environment"),
        @Index(name = "idx_service_registry_zone", columnList = "zone"),
        @Index(name = "idx_service_registry_heartbeat", columnList = "last_heartbeat")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_service_id", columnNames = "service_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"instances"})
@EqualsAndHashCode(callSuper = true, exclude = {"instances"})
public class ServiceRegistry extends BaseEntity {

    @NotBlank(message = "Service ID is required")
    @Size(max = 100, message = "Service ID must not exceed 100 characters")
    @Column(name = "service_id", nullable = false, unique = true, length = 100)
    private String serviceId;

    @NotBlank(message = "Service name is required")
    @Size(max = 255, message = "Service name must not exceed 255 characters")
    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @NotBlank(message = "Service version is required")
    @Size(max = 50, message = "Service version must not exceed 50 characters")
    @Column(name = "service_version", nullable = false, length = 50)
    private String serviceVersion;

    @NotBlank(message = "Service type is required")
    @Size(max = 50, message = "Service type must not exceed 50 characters")
    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "Service host is required")
    @Size(max = 255, message = "Service host must not exceed 255 characters")
    @Column(name = "service_host", nullable = false, length = 255)
    private String serviceHost;

    @NotNull(message = "Service port is required")
    @Min(value = 1, message = "Service port must be at least 1")
    @Max(value = 65535, message = "Service port must not exceed 65535")
    @Column(name = "service_port", nullable = false)
    private Integer servicePort;

    @Size(max = 10, message = "Service scheme must not exceed 10 characters")
    @Column(name = "service_scheme", length = 10)
    @Builder.Default
    private String serviceScheme = "http";

    @Size(max = 500, message = "Service path must not exceed 500 characters")
    @Column(name = "service_path", length = 500)
    @Builder.Default
    private String servicePath = "/";

    @Size(max = 1000, message = "Health check URL must not exceed 1000 characters")
    @Column(name = "health_check_url", length = 1000)
    private String healthCheckUrl;

    @Size(max = 1000, message = "Admin URL must not exceed 1000 characters")
    @Column(name = "admin_url", length = 1000)
    private String adminUrl;

    @Size(max = 1000, message = "Metrics URL must not exceed 1000 characters")
    @Column(name = "metrics_url", length = 1000)
    private String metricsUrl;

    @Size(max = 1000, message = "Documentation URL must not exceed 1000 characters")
    @Column(name = "documentation_url", length = 1000)
    private String documentationUrl;

    @NotNull(message = "Service status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "service_status", nullable = false, length = 20)
    @Builder.Default
    private ServiceStatus serviceStatus = ServiceStatus.UP;

    @NotNull(message = "Last heartbeat is required")
    @Column(name = "last_heartbeat", nullable = false)
    @Builder.Default
    private Instant lastHeartbeat = Instant.now();

    @NotNull(message = "Registration time is required")
    @Column(name = "registration_time", nullable = false)
    @Builder.Default
    private Instant registrationTime = Instant.now();

    @NotNull(message = "Last updated time is required")
    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private Instant lastUpdated = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "tags", columnDefinition = "text[]")
    @Builder.Default
    private String[] tags = new String[0];

    @Size(max = 100, message = "Zone must not exceed 100 characters")
    @Column(name = "zone", length = 100)
    private String zone;

    @Size(max = 100, message = "Region must not exceed 100 characters")
    @Column(name = "region", length = 100)
    private String region;

    @Size(max = 100, message = "Datacenter must not exceed 100 characters")
    @Column(name = "datacenter", length = 100)
    private String datacenter;

    @NotBlank(message = "Environment is required")
    @Size(max = 20, message = "Environment must not exceed 20 characters")
    @Column(name = "environment", nullable = false, length = 20)
    @Builder.Default
    private String environment = "PRODUCTION";

    @Min(value = 0, message = "Weight must not be negative")
    @Max(value = 1000, message = "Weight must not exceed 1000")
    @Column(name = "weight")
    @Builder.Default
    private Integer weight = 100;

    @Column(name = "is_secure")
    @Builder.Default
    private Boolean isSecure = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ssl_info", columnDefinition = "jsonb")
    private Map<String, Object> sslInfo;

    @Size(max = 20, message = "Auto deregister critical after must not exceed 20 characters")
    @Column(name = "auto_deregister_critical_after", length = 20)
    @Builder.Default
    private String autoDeregisterCriticalAfter = "30m";

    @Size(max = 20, message = "Deregister critical service after must not exceed 20 characters")
    @Column(name = "deregister_critical_service_after", length = 20)
    @Builder.Default
    private String deregisterCriticalServiceAfter = "90m";

    // Relationships
    @OneToMany(mappedBy = "serviceRegistry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ServiceInstance> instances = new HashSet<>();

    // Business Methods

    /**
     * Add a service instance
     *
     * @param instance the instance to add
     */
    public void addInstance(ServiceInstance instance) {
        instances.add(instance);
        instance.setServiceRegistry(this);
    }

    /**
     * Remove a service instance
     *
     * @param instance the instance to remove
     */
    public void removeInstance(ServiceInstance instance) {
        instances.remove(instance);
        instance.setServiceRegistry(null);
    }

    /**
     * Update heartbeat timestamp
     */
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * Check if service is healthy based on last heartbeat
     *
     * @param timeoutSeconds heartbeat timeout in seconds
     * @return true if service is healthy
     */
    public boolean isHealthy(long timeoutSeconds) {
        if (serviceStatus != ServiceStatus.UP) {
            return false;
        }
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        return lastHeartbeat.isAfter(cutoff);
    }

    /**
     * Get service URL
     *
     * @return full service URL
     */
    public String getServiceUrl() {
        return String.format("%s://%s:%d%s",
            serviceScheme,
            serviceHost,
            servicePort,
            servicePath);
    }

    /**
     * Check if service is in production environment
     *
     * @return true if production
     */
    public boolean isProduction() {
        return "PRODUCTION".equalsIgnoreCase(environment) || "PROD".equalsIgnoreCase(environment);
    }

    /**
     * Check if service has instances
     *
     * @return true if has instances
     */
    public boolean hasInstances() {
        return instances != null && !instances.isEmpty();
    }

    /**
     * Get count of healthy instances
     *
     * @return count of healthy instances
     */
    public long getHealthyInstanceCount() {
        if (instances == null) {
            return 0;
        }
        return instances.stream()
            .filter(instance -> instance.getHealthStatus() == HealthStatus.HEALTHY)
            .count();
    }

    /**
     * Add metadata entry
     *
     * @param key metadata key
     * @param value metadata value
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
    }

    /**
     * Add tag
     *
     * @param tag tag to add
     */
    public void addTag(String tag) {
        if (tags == null) {
            tags = new String[]{tag};
        } else {
            String[] newTags = new String[tags.length + 1];
            System.arraycopy(tags, 0, newTags, 0, tags.length);
            newTags[tags.length] = tag;
            tags = newTags;
        }
    }

    @PrePersist
    protected void onServiceRegistryCreate() {
        if (registrationTime == null) {
            registrationTime = Instant.now();
        }
        if (lastHeartbeat == null) {
            lastHeartbeat = Instant.now();
        }
        if (lastUpdated == null) {
            lastUpdated = Instant.now();
        }
    }

    @PreUpdate
    protected void onServiceRegistryUpdate() {
        lastUpdated = Instant.now();
    }
}
